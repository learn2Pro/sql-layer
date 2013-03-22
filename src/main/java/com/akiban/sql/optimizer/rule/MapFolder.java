/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.akiban.sql.optimizer.rule;

import com.akiban.sql.optimizer.plan.*;

import com.akiban.server.error.UnsupportedSQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/** Take a map join node and push enough into the inner loop that the
 * bindings can be evaluated properly. 
 * Or, looked at another way, what is before expressed through
 * data-flow is after expressed as control-flow.
 */
public class MapFolder extends BaseRule
{
    private static final Logger logger = LoggerFactory.getLogger(MapFolder.class);

    @Override
    protected Logger getLogger() {
        return logger;
    }

    static class MapJoinsFinder implements PlanVisitor, ExpressionVisitor {
        List<MapJoin> result;

        public List<MapJoin> find(PlanNode root) {
            result = new ArrayList<>();
            root.accept(this);
            return result;
        }

        @Override
        public boolean visitEnter(PlanNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(PlanNode n) {
            return true;
        }

        @Override
        public boolean visit(PlanNode n) {
            if (n instanceof MapJoin) {
                result.add((MapJoin)n);
            }
            return true;
        }

        @Override
        public boolean visitEnter(ExpressionNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(ExpressionNode n) {
            return true;
        }

        @Override
        public boolean visit(ExpressionNode n) {
            return true;
        }
    }

    static class ColumnSourceFinder implements PlanVisitor { // Not down to expressions.
        Set<ColumnSource> result;

        public Set<ColumnSource> find(PlanNode root) {
            result = new HashSet<>();
            root.accept(this);
            return result;
        }

        @Override
        public boolean visitEnter(PlanNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(PlanNode n) {
            return true;
        }

        @Override
        public boolean visit(PlanNode n) {
            if (n instanceof ColumnSource) {
                result.add((ColumnSource)n);
            }
            else if (n instanceof IndexScan) {
                result.addAll(((IndexScan)n).getTables());
            }
            return true;
        }
    }

    @Override
    public void apply(PlanContext planContext) {
        BaseQuery query = (BaseQuery)planContext.getPlan();
        List<MapJoin> maps = new MapJoinsFinder().find(query);
        List<MapJoinProject> mapJoinProjects = new ArrayList<>(0);
        if (maps.isEmpty()) return;
        if (query instanceof DMLStatement) {
            DMLStatement update = (DMLStatement)query;
            switch (update.getType()) {
            case UPDATE:
            case DELETE:
                addUpdateInput(update);
                break;
            }
        }
        for (MapJoin map : maps)
            handleJoinType(map);
        for (MapJoin map : maps)
            foldOuterMap(map);
        for (MapJoin map : maps)
            fold(map, mapJoinProjects);
        for (MapJoinProject project : mapJoinProjects)
            fillProject(project);
    }

    // First pass: account for the join type by adding something at
    // the tip of the inner (fast) side of the loop.
    protected void handleJoinType(MapJoin map) {
        switch (map.getJoinType()) {
        case INNER:
            break;
        case LEFT:
            map.setInner(new NullIfEmpty(map.getInner()));
            break;
        case SEMI:
            {
                PlanNode inner = map.getInner();
                if ((inner instanceof Select) && 
                    ((Select)inner).getConditions().isEmpty())
                    inner = ((Select)inner).getInput();
                // Right-nested semi-joins only need one Limit, since
                // all the effects happen inside the fastest loop.
                if (!((inner instanceof MapJoin) && 
                      ((MapJoin)inner).getJoinType() == JoinNode.JoinType.SEMI))
                    inner = new Limit(map.getInner(), 1);
                map.setInner(inner);
            }
            break;
        case ANTI:
            map.setInner(new OnlyIfEmpty(map.getInner()));
            break;
        default:
            throw new UnsupportedSQLException("complex join type " + map, null);
        }
        map.setJoinType(null);  // No longer special.
    }

    // Second pass: if one map has another on the outer (slow) side,
    // turn them inside out. Nesting must all be on the inner side to
    // be like regular loops. Conceptually, the two trade places, but
    // actually doing that would mess up the depth nesting for the
    // next pass.
    protected void foldOuterMap(MapJoin map) {
        if (map.getOuter() instanceof MapJoin) {
            MapJoin otherMap = (MapJoin)map.getOuter();
            foldOuterMap(otherMap);
            PlanNode inner = map.getInner();
            PlanNode outer = otherMap.getInner();
            map.setOuter(otherMap.getOuter());
            map.setInner(otherMap);
            otherMap.setOuter(outer);
            otherMap.setInner(inner);
        }
    }    

    // Third pass: move things upstream of the map down into the inner (fast) side.
    // Also add Project where the nesting still needs an actual join
    // on the outer side.
    protected void fold(MapJoin map, List<MapJoinProject> mapJoinProjects) {
        PlanWithInput parent = map;
        PlanNode child;
        do {
            child = parent;
            parent = child.getOutput();
        } while (!((parent instanceof MapJoin) ||
                   // These need to be outside.
                   (parent instanceof Subquery) ||
                   (parent instanceof ResultSet) ||
                   (parent instanceof AggregateSource) ||
                   (parent instanceof Sort) ||
                   // Captures enough at the edge of the inside.
                   (child instanceof Project) ||
                   (child instanceof UpdateInput)));
        if (child != map) {
            PlanNode inner = map.getInner();
            if (parent instanceof MapJoin) {
                MapJoinProject nested = findAddedProject((MapJoin)parent, 
                                                         mapJoinProjects);
                if ((nested != null) ||
                    (child == ((MapJoin)parent).getOuter())) {
                    inner = addProject((MapJoin)parent, map, inner,
                                       nested, mapJoinProjects);
                }
            }
            else if (child instanceof Project) {
                MapJoinProject nested = findAddedProject((Project)child,
                                                         mapJoinProjects);
                if (nested != null) {
                    inner = addProject(null, map, inner,
                                       nested, mapJoinProjects);
                }
            }
            map.getOutput().replaceInput(map, inner);
            parent.replaceInput(child, map);
            map.setInner(child);
        }
    }

    static class MapJoinProject implements PlanVisitor, ExpressionVisitor {
        MapJoin parentMap, childMap;
        MapJoinProject nested;
        Project project;
        Set<ColumnSource> allSources, innerSources;
        List<ColumnExpression> columns;
        boolean foundOuter;

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder(getClass().getSimpleName());
            str.append("(").append(childMap.summaryString());
            for (ColumnSource source : allSources) {
                str.append(",");
                if (innerSources.contains(source))
                    str.append("*");
                str.append(source.getName());
            }
            if (project != null) {
                str.append(",").append(project.getFields());
            }
            str.append(")");
            return str.toString();
        }

        public MapJoinProject(MapJoin parentMap, MapJoin childMap, 
                              MapJoinProject nested, Project project,
                              Set<ColumnSource> allSources, 
                              Set<ColumnSource> innerSources) {
            this.parentMap = parentMap;
            this.childMap = childMap;
            this.nested = nested;
            this.project = project;
            this.allSources = allSources;
            this.innerSources = innerSources;
        }
        
        public boolean find() {
            columns = new ArrayList<>();
            for (MapJoinProject loop = this; loop != null; loop = loop.nested) {
                if (loop.parentMap != null) {
                    // Check context within the bindings of any nested loops.
                    loop.parentMap.getInner().accept(this);
                }
            }
            return foundOuter;
        }

        public void install() {
            Set<ColumnExpression> seen = new HashSet<>();
            for (ColumnExpression column : columns) {
                if (seen.add(column)) {
                    project.getFields().add(column);
                }
            }
        }

        public void remove() {
            project.getOutput().replaceInput(project, project.getInput());
            project = null;
        }

        @Override
        public boolean visitEnter(PlanNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(PlanNode n) {
            return true;
        }

        @Override
        public boolean visit(PlanNode n) {
            return true;
        }

        @Override
        public boolean visitEnter(ExpressionNode n) {
            return visit(n);
        }

        @Override
        public boolean visitLeave(ExpressionNode n) {
            return true;
        }

        @Override
        public boolean visit(ExpressionNode n) {
            if (n instanceof ColumnExpression) {
                ColumnExpression column = (ColumnExpression)n;
                if (allSources.contains(column.getTable())) {
                    columns.add(column);
                    if (!innerSources.contains(column.getTable())) {
                        foundOuter = true;
                    }
                }
            }
            return true;
        }
     }

    protected Project addProject(MapJoin parentMap, MapJoin childMap, PlanNode inner, 
                                 MapJoinProject nested, List<MapJoinProject> into) {
        ColumnSourceFinder finder = new ColumnSourceFinder();
        Set<ColumnSource> outerSources = finder.find(childMap.getOuter());
        Set<ColumnSource> innerSources = finder.find(childMap.getInner());
        outerSources.addAll(innerSources);
        Project project = new Project(inner, new ArrayList<ExpressionNode>());
        into.add(new MapJoinProject(parentMap, childMap, 
                                    nested, project,
                                    outerSources, innerSources));
        return project;
    }

    protected MapJoinProject findAddedProject(MapJoin childMap, List<MapJoinProject> in) {
        for (MapJoinProject mapJoinProject : in) {
            if (mapJoinProject.childMap == childMap) {
                return mapJoinProject;
            }
        }
        return null;
    }
    
    protected MapJoinProject findAddedProject(Project project, List<MapJoinProject> in) {
        for (MapJoinProject mapJoinProject : in) {
            if (mapJoinProject.project == project) {
                return mapJoinProject;
            }
        }
        return null;
    }
    
    // Fourth pass: materialize join with a Project when there is no
    // other alternative.
    protected void fillProject(MapJoinProject project) {
        if (project.find()) {
            project.install();
            logger.debug("Added {}", project);
        }
        else {
            project.remove();   // Everything came from inner table(s) after all.
            logger.debug("Skipped {}", project);
        }
    }

    protected void addUpdateInput(DMLStatement update) {
        BasePlanWithInput node = update;
        while (true) {
            PlanNode input = node.getInput();
            if (!(input instanceof BasePlanWithInput))
                break;
            node = (BasePlanWithInput)input;
            if (node instanceof BaseUpdateStatement) {
                input = node.getInput();
                node.replaceInput(input, new UpdateInput(input, update.getSelectTable()));
                return;
            }
        }
    }

}
