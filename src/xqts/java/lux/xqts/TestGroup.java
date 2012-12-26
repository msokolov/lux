package lux.xqts;

import java.io.IOException;
import java.util.ArrayList;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

public class TestGroup {
    private final int depth;
    private final String name;
    private final ArrayList<TestGroup> subgroups = new ArrayList<TestGroup>();
    private final ArrayList<TestCase> cases = new ArrayList<TestCase>();
    
    static final QName TEST_CASE = new QName (TestCase.XQTS_NS, "test-case");
    static final QName TEST_GROUP = new QName(TestCase.XQTS_NS, "test-group");
    
    public TestGroup (XdmNode testGroupNode, Catalog catalog) throws IOException, SaxonApiException {
        this (testGroupNode, catalog, 1);
    }

    private TestGroup (XdmNode testGroupNode, Catalog catalog, int depth) throws IOException, SaxonApiException {
        name = testGroupNode.getAttributeValue(TestCase.NAME);
        //System.out.println ("reading test group " + name);
        this.depth = depth; 
        XdmSequenceIterator children = testGroupNode.axisIterator(net.sf.saxon.s9api.Axis.CHILD);
        while (children.hasNext()) {
            XdmNode child = (XdmNode) children.next();
            if (child.getNodeName() == null) {
                continue;
            }
            if (child.getNodeName().equals(TEST_GROUP)) {
                TestGroup subgroup = new TestGroup (child, catalog, depth + 1);
                subgroups.add(subgroup);
            }
            else if (child.getNodeName().equals(TEST_CASE)) {
                TestCase test = new TestCase(child, catalog);
                cases.add(test);                
            }
        }
    }
    
    public String getName () {
        return name;
    }
    
    public int getDepth () {
        return depth;
    }

    public TestGroup getTestGroupByName(String groupName) {
        if (this.name.equals(groupName)) {
            return this;
        }
        for (TestGroup testGroup : subgroups) {
            TestGroup group = testGroup.getTestGroupByName (groupName);
            if (group != null)
                return group;
        }
        return null;
    }
    
    public Iterable<TestCase> getTestCases () {
        return cases;
    }

    public Iterable<TestGroup> getSubGroups() {
        return subgroups;
    }
    
    public String getBannerString () {
        StringBuilder buf = new StringBuilder();
        appendStars(buf);
        buf.append (' ');
        buf.append(getName());
        return buf.toString();
    }

    private void appendStars(StringBuilder buf) {
        for (int i = 1; i < 5; i++) {
            if (i >= depth) {
                buf.append('*');
            } else {
                buf.append(' ');
            }
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
