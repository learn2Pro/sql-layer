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

package com.akiban.ais.model.staticgrouping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Join;
import com.akiban.ais.model.JoinColumn;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.UserTable;

public final class GroupsBuilder
{
    private final Grouping grouping;
    /**
     * <p>Joins that have not yet been fully constructed.</p>
     *
     * <p>Users of this builder class may want to build a JoinDescription by first specifying only the child's table
     * name, and then adding each column as it comes. JoinDescription objects must always have at least one parent/child
     * column pairing, though. To get around this problem, when the user specifies only the child table name, we
     * defer creation of the JoinDescription, creating instead a JoinDescriptionBuilder. This builder will notify
     * us when it gets its first column pair -- that is, when it's able to first instantiate the JoinDescription.</p>
     *
     * <p>As a sanity check for consumers of this GroupsBuilder class, we want to keep a list of JoinDescriptionBuilders
     * which have not yet reported in. Each of those is an error, and getGrouping() will throw an exception if it
     * sees this.</p>
     */
    private final Set<JoinDescriptionBuilder> unfinishedJoins = new HashSet<>();

    private JoinDescriptionBuilder lastJoinBuilder;

    private final List<TableName> parentTables = new ArrayList<>();

    private final JoinDescriptionBuilder.Callback joinBuilderCallback = new JoinDescriptionBuilder.Callback() {
        @Override
        public JoinDescription created(JoinDescriptionBuilder builder,
                                       TableName parent, String firstParentColumn,
                                       TableName child, String firstChildColumn)
        {
            if (!unfinishedJoins.remove(builder)) {
                throw new IllegalStateException("builder not present: " + builder);
            }

            List<String> childCols = Arrays.asList(firstChildColumn);
            List<String> parentCols = Arrays.asList(firstParentColumn);
            return grouping.addChildTable(parent, parentCols, child, childCols);
        }
    };

    private final static GroupingVisitor<GroupsBuilder> COPIER = new GroupingVisitorStub<GroupsBuilder>() {

        GroupsBuilder builder;

        @Override
        public void start(String defaultSchema) {
            builder = new GroupsBuilder(defaultSchema);
        }

        @Override
        public void visitGroup(Group group, TableName rootTable) {
            builder.rootTable(rootTable, group.getGroupName());
        }

        @Override
        public void visitChild(TableName parentName, List<String> parentColumns, TableName childName, List<String> childColumns) {
            builder.joinTables(parentName, parentColumns, childName, childColumns);
        }

        @Override
        public GroupsBuilder end() {
            return builder;
        }
    };

    public static GroupsBuilder from(Grouping source) {
        synchronized (COPIER) {
            return source.traverse(COPIER);
        }
    }

    /**
     * Creates a GroupsBuilder that works on the given Grouping object. If you use this constructor,
     * {@link #getGrouping()} will return a reference to the same object you passed to this constructor.
     * @param grouping the grouping object to be modified.
     */
    public GroupsBuilder(Grouping grouping) {
        this.grouping = grouping;
    }

    /**
     * Creates a GroupsBuilder that works on a new, empty Grouping object. If you use this constructor, you must
     * add at least one group before invoking {@linkplain #getGrouping()}, or that method will throw an unchecked
     * exception due to the grouping being invalid.
     * @param defaultSchema the new grouping's default schema
     */
    public GroupsBuilder(String defaultSchema) {
        grouping = new Grouping(defaultSchema);
    }

    public Grouping getGrouping() {
        if (! unfinishedJoins.isEmpty()) {
            throw new IllegalStateException("uncompleted join(s): " + unfinishedJoins);
        }
        return grouping;
    }

    public void rootTable(TableName root, String groupName) {
        rootTable(root, new TableName(root.getSchemaName(), groupName));
    }

    public void rootTable(TableName root, TableName groupName) {
        Group group = new Group(groupName);
        grouping.addGroup(group, root);
    }

    public void rootTable(String schemaName, String tableName, TableName group) {
        rootTable(new TableName(schemaName, tableName), group);
    }

    public void rootTable(String schemaName, String tableName, String group) {
        rootTable(new TableName(schemaName, tableName), group);
    }

    public void rootTable(String tableName, String group) {
        rootTable(grouping.getDefaultSchema(), tableName, group);
    }

    public JoinDescriptionBuilder joinTables(TableName parent, TableName child) {
        JoinDescriptionBuilder builder = new JoinDescriptionBuilder(parent, child, joinBuilderCallback);
        lastJoinBuilder = builder;
        unfinishedJoins.add(builder);
        return builder;
    }

    public JoinDescriptionBuilder joinTables(String parentSchemaName, String parentTableName,
                                             String childSchemaName, String childTableName) {
        return joinTables(
                new TableName(parentSchemaName, parentTableName),
                new TableName(childSchemaName, childTableName)
        );
    }

    public JoinDescriptionBuilder joinTables(TableName child) {
        return joinTables(parentTables.get(parentTables.size()-1), child);
    }

    public JoinDescriptionBuilder joinTables(String schemaName, String tableName) {
        return joinTables(TableName.create(schemaName, tableName));
    }

    public JoinDescriptionBuilder joinTables(String tableName) {
        return joinTables(grouping.getDefaultSchema(), tableName);
    }

    public JoinDescriptionBuilder getLastJoinTablesBuilder() {
        return lastJoinBuilder;
    }

    public void joinTables(TableName parentName, List<String> parentColumns, TableName childName, List<String> childColumns) {
        if (parentColumns.size() != childColumns.size()) {
            throw new IllegalArgumentException("mismatch in list lengths between " + parentColumns + " and " + childColumns);
        }
        JoinDescriptionBuilder joinBuilder = joinTables(parentName, childName);
        Iterator<String> childCols = childColumns.iterator();

        for (String parentCol : parentColumns) {
            joinBuilder.column(parentCol, childCols.next());
        }
        assert ! childCols.hasNext() : "had extra child columns: " + parentColumns + childColumns;
    }

    public void dropGroup(TableName groupName) {
        grouping.dropGroup(groupName);
    }

    public void startChildren(TableName tableName) {
        parentTables.add(tableName);
    }

    public void startChildren(String schemaName, String tableName) {
        startChildren( TableName.create(schemaName, tableName) );
    }

    public void startChildren(String tableName) {
        startChildren( grouping.getDefaultSchema(), tableName );
    }

    public void endChildren() {
        parentTables.remove(parentTables.size()-1);
    }


    public static Grouping fromAis(AkibanInformationSchema ais, String defaultSchema) {
        GroupsBuilder builder = new GroupsBuilder(defaultSchema);

        for (UserTable uTable : ais.getUserTables().values()) {
            if (uTable.getGroup() == null) {
                continue;
            }
            final String groupName;
            if (uTable.getGroup().getRoot() == uTable) {
                groupName = "__GROUP_" + uTable.getGroup().getName().getTableName();
            }
            else {
                groupName = "__TMP_@" + System.identityHashCode(uTable) + '_' + uTable.getName();
            }
            builder.rootTable(uTable.getName(), groupName);
        }

        Grouping grouping = builder.getGrouping();
        for (Join join : ais.getJoins().values()) {
            if (join.getGroup() == null) {
                continue;
            }
            List<String> childColumns = new ArrayList<>();
            List<String> parentColumns = new ArrayList<>();
            for (JoinColumn joinCol : join.getJoinColumns()) {
                childColumns.add(joinCol.getChild().getName());
                parentColumns.add(joinCol.getParent().getName());
                grouping.moveChild(join.getChild().getName(), join.getParent().getName(), childColumns, parentColumns);
            }
        }

        for (Group group : grouping.getGroups()) {
            assert group.getGroupName().getTableName().startsWith("__GROUP_") : group.getGroupName();
            String unmangledName = group.getGroupName().getTableName().substring("__GROUP_".length());
            grouping.newGroupFromChild(grouping.getRootTable(group), new TableName(group.getGroupName().getSchemaName(), unmangledName));
        }
        
        return grouping;
    }
}
