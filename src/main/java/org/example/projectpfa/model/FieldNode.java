package org.example.projectpfa.model;

import java.util.ArrayList;
import java.util.List;

public class FieldNode {
    String fieldName;
    String dataType;
    String parent;
    String occurrence;
    String fieldDescription;
    List<FieldNode> children = new ArrayList<>();

    public FieldNode(String fieldName, String dataType, String parent, String occurrence, String fieldDescription) {
        super();
        this.fieldName = fieldName;
        this.dataType = dataType;
        this.parent = parent;
        this.occurrence = occurrence;
        this.fieldDescription = fieldDescription;
    }

    public FieldNode() {}
    public void addChild(FieldNode child) {
        children.add(child);
    }

    public List<FieldNode> getChildren() {
        return children;
    }

    // getters

    public String getFieldName() { return fieldName; }
    public String getDataType() { return dataType; }
    public String getOccurrence() { return occurrence; }
    public String getFieldDescription() { return fieldDescription; }
    public String getParent() { return parent; }

    @Override
    public String toString() {
        return fieldName + " [" + parent + "] ";//+ " (" + fieldDescription + ")";
    }
}
