package org.example.projectpfa.service;

import org.apache.commons.lang3.StringUtils;
import org.example.projectpfa.model.FieldNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.projectpfa.service.GeneratorService.isComplexType;

public class TreeBuilderService {
    public static FieldNode buildTree(String parentPath, Map<String, List<FieldNode>> map) {
        List<FieldNode> children = map.get(parentPath);
        if (children == null) return null;


        // Since root may be multiple fields, return a dummy root node
        FieldNode dummyRoot = new FieldNode(parentPath, "Complex","Root", "Required", "Root node");
        for (FieldNode child : children) {
            String childPath = buildPath(parentPath, child.getFieldName());
            if (isComplexType(child.getDataType())) {
                FieldNode subtree = buildTree(childPath, map);
                if (subtree != null) {
                    child.getChildren().addAll(subtree.getChildren());
                }
            }
            dummyRoot.addChild(child);
        }

        return dummyRoot;
    }

    public static String buildPath(String parentPath, String fieldName) {
        if (parentPath == null || parentPath.isEmpty() || parentPath.equals("-")) {
            return fieldName;
        } else {
            return parentPath + "." + fieldName;
        }
    }

    public static Map<String, String> buildFieldPaths(List<FieldNode> fields) {
        Map<String, String> fieldToParent = new HashMap<>();
        for (FieldNode f : fields) {
            fieldToParent.put(StringUtils.uncapitalize(f.getFieldName()), StringUtils.uncapitalize(f.getParent()));
        }

        Map<String, String> fieldToPath = new HashMap<>();
        for (FieldNode f : fields) {
            StringBuilder path = new StringBuilder(StringUtils.uncapitalize(f.getFieldName()));
            String parent = StringUtils.uncapitalize(f.getParent());
            while (parent != null && !parent.equals("-")) {
                path.insert(0, parent + ".");
                parent = fieldToParent.get(parent);
            }
            fieldToPath.put(StringUtils.uncapitalize(f.getFieldName()), path.toString());
        }
        return fieldToPath;
    }

}
