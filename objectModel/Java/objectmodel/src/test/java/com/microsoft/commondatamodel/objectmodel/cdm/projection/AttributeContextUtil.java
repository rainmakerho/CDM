// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

package com.microsoft.commondatamodel.objectmodel.cdm.projection;

import com.microsoft.commondatamodel.objectmodel.cdm.*;
import com.microsoft.commondatamodel.objectmodel.enums.CdmObjectType;
import com.microsoft.commondatamodel.objectmodel.utilities.StringUtils;
import org.testng.Assert;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Multiple test classes in projections test the attribute context tree generated for various scenarios.
 * This utility class helps generate the actual attribute context generated by the scenario, so that it can be compared with expected attribute context tree.
 * This also handles the validation of the expected vs. actual attribute context.
 */
public final class AttributeContextUtil {
    private StringBuilder bldr = new StringBuilder();

    /**
     * Platform-specific line separator
     */
    private String endOfLine = System.getProperty("line.separator");

    /**
     * Function to get the attribute context string tree from a resolved entity
     */
    public String getAttributeContextStrings(CdmEntityDefinition resolvedEntity, CdmAttributeContext attribContext) {
        // clear the string builder
        bldr.setLength(0);

        // get the corpus path for each attribute context in the tree
        getContentDeclaredPath(resolvedEntity.getAttributeContext());

        // get the traits for all the attributes of a resolved entity
        getTraits(resolvedEntity);

        return bldr.toString();
    }

    /**
     * Get the corpus path for each attribute context in the tree and build a string collection that we can
     * compare with the expected attribute context corpus path collection.
     */
    private void getContentDeclaredPath(CdmAttributeContext attribContext) {
        if (attribContext != null && attribContext.getContents() != null && attribContext.getContents().size() > 0) {
            for (int i = 0; i < attribContext.getContents().size(); i++) {
                String str = "";
                if ((attribContext.getContents().get(0) instanceof CdmAttributeReference)) {
                    CdmAttributeReference ar = (CdmAttributeReference) attribContext.getContents().get(i);
                    str = ar.getAtCorpusPath();
                    bldr.append(str);
                    bldr.append(endOfLine);
                } else {
                    CdmAttributeContext ac = (CdmAttributeContext) attribContext.getContents().get(i);
                    str = ac.getAtCorpusPath();
                    bldr.append(str);
                    bldr.append(endOfLine);
                    getContentDeclaredPath(ac);
                }
            }
        }
    }

    /**
     * Get the traits for all the attributes of a resolved entity
     */
    private void getTraits(CdmEntityDefinition resolvedEntity) {
        for (CdmAttributeItem attrib : resolvedEntity.getAttributes()) {
            String attribCorpusPath = attrib.getAtCorpusPath();
            bldr.append(attribCorpusPath);
            bldr.append(endOfLine);

            for (CdmTraitReference trait : attrib.getAppliedTraits()) {
                String attribTraits = trait.getNamedReference();
                bldr.append(attribTraits);
                bldr.append(endOfLine);

                for (CdmArgumentDefinition args : trait.getArguments()) {
                    getArgumentValuesAsString(args);
                }
            }
        }
    }

    private void getArgumentValuesAsString(CdmArgumentDefinition args) {
        String paramName = args.getResolvedParameter() != null ? args.getResolvedParameter().getName() : null;
        String paramDefaultValue = args.getResolvedParameter() != null ? (String) args.getResolvedParameter().getDefaultValue() : null;

        if (!StringUtils.isNullOrTrimEmpty(paramName) || !StringUtils.isNullOrTrimEmpty(paramDefaultValue)) {
            bldr.append("  [Parameter (Name / DefaultValue): " + (paramName != null ? paramName : "") + " / " + (paramDefaultValue != null ? paramDefaultValue : "") + "]");
            bldr.append(endOfLine);
        }

        if (args.getValue() instanceof String) {
            String argsValue = (String) args.getValue();

            if (!StringUtils.isNullOrTrimEmpty(argsValue)) {
                bldr.append("  [Argument Value: " + argsValue + "]");
                bldr.append(endOfLine);
            }
        } else if (args.getValue() != null ? ((CdmObjectReference) args.getValue()).isSimpleNamedReference() == true : false) {
            String argsValue = ((CdmObjectReference) args.getValue()).getNamedReference();

            if (!StringUtils.isNullOrTrimEmpty(argsValue)) {
                bldr.append("  [Argument Value: " + argsValue + "]");
                bldr.append(endOfLine);
            }
        } else if (args.getValue() != null ? ((CdmObjectReference) args.getValue()).getExplicitReference().getObjectType() == CdmObjectType.ConstantEntityDef : false) {
            CdmConstantEntityDefinition constEnt = (CdmConstantEntityDefinition) ((CdmObjectReferenceBase) args.getValue()).getExplicitReference();
            if (constEnt != null) {
                List<CdmEntityDefinition> refs = new ArrayList<>();
                for (List<String> val : constEnt.getConstantValues()) {
                    bldr.append("  [Argument Value: " + String.join(",", val.toArray(new String[val.size()])) + "]");
                    bldr.append(endOfLine);
                }
            }
        }
    }

    /**
     * A function to validate if the attribute context tree & traits generated for a resolved entity is the same as
     * the expected and saved attribute context tree & traits for a test case
     */
    public static void validateAttributeContext(CdmCorpusDefinition corpus, String expectedOutputPath, String entityName, CdmEntityDefinition resolvedEntity) {
        if (resolvedEntity.getAttributeContext() != null) {
            AttributeContextUtil attrCtxUtil = new AttributeContextUtil();

            try {
                // Expected
                Path expectedStringFilePath = new File(expectedOutputPath, "AttrCtx_" + entityName + ".txt").toPath();
                final String expectedText = new String(Files.readAllBytes(expectedStringFilePath), StandardCharsets.UTF_8);

                // Actual
                Path actualStringFilePath = new File(expectedOutputPath.replace("ExpectedOutput", "ActualOutput"), "AttrCtx_" + entityName + ".txt").toPath();
                String actualText = attrCtxUtil.getAttributeContextStrings(resolvedEntity, resolvedEntity.getAttributeContext());

                // Test if Actual is Equal to Expected
                Assert.assertEquals(actualText, expectedText);

                // Save Actual AttrCtx_*.txt and Resolved_*.cdm.json
                try (final BufferedWriter actualFileWriter = Files.newBufferedWriter(actualStringFilePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE);) {
                    actualFileWriter.write(actualText);
                    actualFileWriter.flush();
                }
                resolvedEntity.getInDocument().saveAsAsync("Resolved_" + entityName + ".cdm.json", false).join();
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
        }
    }
}
