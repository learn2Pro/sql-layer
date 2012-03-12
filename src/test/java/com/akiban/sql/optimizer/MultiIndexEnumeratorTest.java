/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.sql.optimizer;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.Group;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.Join;
import com.akiban.ais.model.JoinColumn;
import com.akiban.ais.model.UserTable;
import com.akiban.junit.NamedParameterizedRunner;
import com.akiban.junit.OnlyIf;
import com.akiban.junit.OnlyIfNot;
import com.akiban.junit.Parameterization;
import com.akiban.junit.ParameterizationBuilder;
import com.akiban.server.rowdata.SchemaFactory;
import com.akiban.sql.optimizer.plan.MultiIndexCandidate;
import com.akiban.sql.optimizer.plan.MultiIndexEnumerator;
import com.akiban.sql.optimizer.plan.MultiIndexEnumerator.MultiIndexPair;
import com.akiban.sql.optimizer.rule.EquivalenceFinder;
import com.akiban.util.AssertUtils;
import com.akiban.util.Strings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(NamedParameterizedRunner.class)
public final class MultiIndexEnumeratorTest {
    
    private static final File TEST_DIR = new File(OptimizerTestBase.RESOURCE_DIR, "multi-index-enumeration");
    private static final File SCHEMA_DIR = new File(TEST_DIR, "schema.ddl");
    private static final String DEFAULT_SCHEMA = "mie";
    
    @NamedParameterizedRunner.TestParameters
    public static Collection<Parameterization> params() throws IOException{
        System.out.println(SCHEMA_DIR.getAbsolutePath());
        List<String> ddlList = Strings.dumpFile(SCHEMA_DIR);
        String[] ddl = ddlList.toArray(new String[ddlList.size()]);
        File[] yamls = TEST_DIR.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".yaml");
            }
        });
        ParameterizationBuilder pb = new ParameterizationBuilder();
        for (File yaml : yamls) {
            String name = yaml.getName();
            name = name.substring(0, name.length() - ".yaml".length());
            pb.add(name, yaml, ddl);
        }
        return pb.asList();
    }
    
    @Test @OnlyIfNot("expectException")
    public void combinations() throws IOException {
        Collection<MultiIndexPair<String>> enumerated = getEnumerations();
        List<Combination> actual = new ArrayList<Combination>(enumerated.size());
        for (MultiIndexEnumerator.MultiIndexPair<String> elem : enumerated) {
            Combination combo = new Combination();
            
            MultiIndexCandidate<String> output = elem.getOutputIndex();
            combo.setOutputIndex(output.getIndex());
            combo.setOutputSkip(output.getPegged());

            MultiIndexCandidate<String> selector = elem.getSelectorIndex();
            combo.setSelectorIndex(selector.getIndex());
            combo.setSelectorSkip(selector.getPegged());

            actual.add(combo);
        }
        List<Combination> combinations = tc.getCombinations();
        Collections.sort(combinations);
        Collections.sort(actual);
        AssertUtils.assertCollectionEquals("enumerations", combinations, actual);
    }

    @Test(expected = DuplicateConditionException.class) @OnlyIf("expectException")
    public void expectError() {
        getEnumerations();
    }

    private Collection<MultiIndexPair<String>> getEnumerations() {
        SchemaFactory factory = new SchemaFactory(DEFAULT_SCHEMA);
        AkibanInformationSchema ais = factory.ais(ddl);
        factory.rowDefCache(ais); // set up indx row compositions

        List<Index> indexes = allIndexes(ais, tc.getUsingIndexes());
        Set<String> conditions = new HashSet<String>(tc.getConditionsOn());
        EquivalenceFinder<Column> columnEquivalences = innerJoinEquivalencies(ais);
        addExtraEquivalencies(tc.getExtraEquivalencies(), ais, columnEquivalences);
        return new StringConditionEnumerator(ais).get(indexes, conditions, columnEquivalences);
    }

    private void addExtraEquivalencies(Map<String, String> equivMap, AkibanInformationSchema ais,
                                       EquivalenceFinder<Column> output)
    {
        for (Map.Entry<String,String> entry : equivMap.entrySet()) {
            String one = entry.getKey();
            String two = entry.getValue();
            Column oneCol = findColumn(one, ais);
            Column twoCol = findColumn(two, ais);
            output.markEquivalent(oneCol, twoCol);
        }
    }

    private static Column findColumn(String qualifiedName, AkibanInformationSchema ais) {
        String[] split = qualifiedName.split("\\.", 2);
        String tableName = split[0];
        String colName = split[1].split("\\s+")[0];
        UserTable table = ais.getUserTable(DEFAULT_SCHEMA, tableName);
        Column column = table.getColumn(colName);
        if (column == null)
            throw new RuntimeException("column not found: " + qualifiedName);
        return column;
    }

    private EquivalenceFinder<Column> innerJoinEquivalencies(AkibanInformationSchema ais) {
        EquivalenceFinder<Column> columnEquivalences = new EquivalenceFinder<Column>();
        for (Group group : ais.getGroups().values()) {
            buildInnerJoinEquivalencies(group.getGroupTable().getRoot(), columnEquivalences);
        }
        return columnEquivalences;
    }

    private void buildInnerJoinEquivalencies(UserTable table, EquivalenceFinder<Column> equivalences) {
        for (Join join : table.getChildJoins()) {
            for (JoinColumn joinColumn : join.getJoinColumns()) {
                equivalences.markEquivalent(joinColumn.getChild(), joinColumn.getParent());
            }
            buildInnerJoinEquivalencies(join.getChild(), equivalences);
        }
    }

    private List<Index> allIndexes(AkibanInformationSchema ais, Set<String> usingIndexes) {
        List<Index> results = new ArrayList<Index>();
        for (Group group : ais.getGroups().values()) {
            addIndexes(group.getIndexes(), results, usingIndexes);
            tableIndexes(group.getGroupTable().getRoot(), results, usingIndexes);
        }
        if (!usingIndexes.isEmpty())
            throw new RuntimeException("unknown index(es): " + usingIndexes);
        return results;
    }
    
    private void tableIndexes(UserTable table, List<Index> output, Set<String> usingIndexes) {
        addIndexes(table.getIndexesIncludingInternal(), output, usingIndexes);
        for (Join join : table.getChildJoins()) {
            UserTable child = join.getChild();
            tableIndexes(child, output, usingIndexes);
        }
    }

    private void addIndexes(Collection<? extends Index> indexes, List<Index> output, Set<String> filter) {
        for (Index index : indexes) {
            String indexName = indexToString(index);
            if (filter.remove(indexName))
                output.add(index);
        }
    }

    private static String indexToString(Index index) {
        return String.format("%s.%s",
                index.leafMostTable().getName().getTableName(),
                index.getIndexName().getName()
        );
    }

    public MultiIndexEnumeratorTest(File yaml, String[] ddl) throws IOException {
        tc = (TestCase) new Yaml().load(new FileReader(yaml));
        this.ddl = ddl;
        this.expectException = tc.isError();
    }
    
    private String[] ddl;
    private TestCase tc;
    public final boolean expectException;
    
    private static class StringConditionEnumerator extends MultiIndexEnumerator<String> {

        @Override
        protected Column columnFromCondition(String condition) {
            return findColumn(condition, ais);
        }

        @Override
        protected void handleDuplicateCondition() {
            throw new DuplicateConditionException();
        }

        private StringConditionEnumerator(AkibanInformationSchema ais) {
            this.ais = ais;
        }

        private AkibanInformationSchema ais;
    }
    
    private static class DuplicateConditionException extends RuntimeException {}

    @SuppressWarnings("unused") // getters and setters used by yaml reflection
    public static class TestCase {
        public Set<String> usingIndexes;
        public Set<String> conditionsOn;
        public List<Combination> combinations;
        public Map<String,String> extraEquivalencies = Collections.emptyMap();
        public boolean isError = false;

        public void setError() {
            isError = true;
        }

        public boolean isError() {
            return isError;
        }
        
        public Set<String> getConditionsOn() {
            return conditionsOn;
        }

        public List<Combination> getCombinations() {
            return combinations;
        }

        public void setConditionsOn(Set<String> conditionsOn) {
            this.conditionsOn = conditionsOn;
        }
        
        public void setUsingIndexes(Set<String> usingIndexes) {
            this.usingIndexes = usingIndexes;
        }
        
        public Set<String> getUsingIndexes() {
            return usingIndexes;
        }

        public Map<String, String> getExtraEquivalencies() {
            return extraEquivalencies;
        }

        public void setExtraEquivalencies(Map<String, String> extraEquivalences) {
            this.extraEquivalencies = extraEquivalences;
        }

        public void setCombinations(List<Combination> combinations) {
            this.combinations = new ArrayList<Combination>(combinations);
            Collections.sort(this.combinations);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("conditions: ").append(conditionsOn).append(", ");
            if (isError)
                sb.append("isError=true");
            else
                sb.append("combinations: ").append(combinations);
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TestCase testCase = (TestCase) o;

            return combinations.equals(testCase.combinations)
                    && conditionsOn.equals(testCase.conditionsOn)
                    && isError == testCase.isError;
        }

        @Override
        public int hashCode() {
            int result = conditionsOn.hashCode();
            result = 31 * result + combinations.hashCode();
            return result;
        }
    }

    @SuppressWarnings("unused") // getters and setters used by yaml reflection
    public static class Combination implements Comparable<Combination> {
        public String outputIndex;
        public String selectorIndex;
        public List<String> outputSkip;
        public List<String> selectorSkip;

        public String getOutputIndex() {
            return outputIndex;
        }
        
        public void setOutputIndex(Index index) {
            setOutputIndex(indexToString(index));
        }

        public void setOutputIndex(String outputIndex) {
            this.outputIndex = outputIndex;
        }

        public String getSelectorIndex() {
            return selectorIndex;
        }

        public void setSelectorIndex(Index index) {
            setSelectorIndex(indexToString(index));
        }

        public void setSelectorIndex(String selectorIndex) {
            this.selectorIndex = selectorIndex;
        }

        public List<String> getOutputSkip() {
            return outputSkip;
        }

        public void setOutputSkip(List<String> outputSkip) {
            this.outputSkip = outputSkip;
        }

        public List<String> getSelectorSkip() {
            return selectorSkip;
        }

        public void setSelectorSkip(List<String> selectorSkip) {
            this.selectorSkip = selectorSkip;
        }

        @Override
        public String toString() {
            return String.format("(output <skip %s from %s> selector: <skip %s from %s>)",
                    getOutputSkip(),
                    getOutputIndex(),
                    getSelectorSkip(),
                    getSelectorIndex()
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Combination that = (Combination) o;

            return outputSkip.equals(that.outputSkip)
                    && selectorSkip.equals(that.selectorSkip)
                    && outputIndex.equals(that.outputIndex)
                    && selectorIndex.equals(that.selectorIndex);
        }

        @Override
        public int hashCode() {
            int result = outputIndex.hashCode();
            result = 31 * result + selectorIndex.hashCode();
            result = 31 * result + outputSkip.hashCode();
            result = 31 * result + selectorSkip.hashCode();
            return result;
        }

        @Override
        public int compareTo(Combination o) {
            int cmp = getOutputIndex().compareTo(o.getOutputIndex());
            if (cmp != 0)
                return cmp;
            cmp = getSelectorIndex().compareTo(o.getSelectorIndex());
            if (cmp != 0)
                return cmp;
            cmp = compare(getOutputSkip(), o.getOutputSkip());
            if (cmp != 0)
                return cmp;
            return compare(getSelectorSkip(), o.getSelectorSkip());
        }

        private int compare(List<String> list1, List<String> list2) {
            return String.valueOf(list1).compareTo(String.valueOf(list2));
        }
    }
}
