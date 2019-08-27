package com.synopsys.integration.blackduck.dockerinspector;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.gson.Gson;
import com.synopsys.integration.bdio.BdioReader;
import com.synopsys.integration.bdio.model.SimpleBdioDocument;
import com.synopsys.integration.blackduck.dockerinspector.programversion.ProgramVersion;
import com.synopsys.integration.blackduck.imageinspector.api.name.Names;
import com.synopsys.integration.exception.IntegrationException;

public class IntegrationTestCommon {
    private static int START_AS_NEEDED_IMAGE_INSPECTOR_PORT_ON_HOST_ALPINE = 8100;
    private static int START_AS_NEEDED_IMAGE_INSPECTOR_PORT_ON_HOST_CENTOS = 8101;
    public static int START_AS_NEEDED_IMAGE_INSPECTOR_PORT_ON_HOST_UBUNTU = 8102;

    public static void testImage(final Random random, final ProgramVersion programVersion, final String detectJarPath, final TestConfig testConfig)
        throws IOException, InterruptedException, IntegrationException {
        final File outputContainerFileSystemFile = getOutputContainerFileSystemFileFromImageSpec(testConfig.getInspectTargetImageRepoTag());
        final String inspectTargetArg = String.format("--docker.image=%s", testConfig.getInspectTargetImageRepoTag());
        ensureFileDoesNotExist(outputContainerFileSystemFile);
        final File actualBdio;
        if (testConfig.getMode() == TestConfig.Mode.DETECT) {
            actualBdio = new File(String.format(String.format("%s/blackduck/bdio/%s_bdio.jsonld", System.getProperty("user.home"), testConfig.getCodelocationName())));
        } else {
            actualBdio = new File(String.format(String.format("%s/output/%s_bdio.jsonld", TestUtils.TEST_DIR_REL_PATH, testConfig.getCodelocationName())));
        }
        ensureFileDoesNotExist(actualBdio);

        final List<String> cmd = createCmd(random, programVersion, testConfig.getMode(), detectJarPath, inspectTargetArg, testConfig.getTargetRepo(), testConfig.getTargetTag(), testConfig.getCodelocationName(),
            testConfig.getAdditionalArgs());

        System.out.println(String.format("Running end to end test on %s with command %s", testConfig.getInspectTargetImageRepoTag(), cmd.toString()));
        TestUtils.execCmd(String.join(" ", cmd), 30000L, true, testConfig.getEnv());
        System.out.println("blackduck-docker-inspector done; verifying results...");
        System.out.printf("Expecting output BDIO file: %s\n", actualBdio.getAbsolutePath());
        assertTrue(actualBdio.exists());
        if (testConfig.isRequireBdioMatch()) {
            final File expectedBdio = new File(String.format(String.format("src/test/resources/bdio/%s_bdio.jsonld", testConfig.getCodelocationName())));
            final List<String> exceptLinesContainingThese = new ArrayList<>();
            exceptLinesContainingThese.add("\"@id\":");
            exceptLinesContainingThese.add("spdx:created");
            exceptLinesContainingThese.add("Tool:");
            final boolean outputBdioMatches = TestUtils.contentEquals(expectedBdio, actualBdio, exceptLinesContainingThese);
            assertTrue(outputBdioMatches);
        }
        final SimpleBdioDocument doc = createBdioDocumentFromFile(actualBdio);
        assertTrue(doc.components.size() >= testConfig.getMinNumberOfComponentsExpected());
        if (StringUtils.isNotBlank(testConfig.getOutputBomMustContainComponentPrefix())) {
            System.out.printf("Looking for component name starting with: %s\n", testConfig.getOutputBomMustContainComponentPrefix());
            boolean componentFound = false;
            for (int i = 0; i < doc.components.size(); i++) {
                System.out.printf("\tComponent: %s / %s\n", doc.components.get(i).name, doc.components.get(i).version);
                if (doc.components.get(i).name.startsWith(testConfig.getOutputBomMustContainComponentPrefix())) {
                    componentFound = true;
                    break;
                }
            }
            assertTrue(componentFound);
            System.out.printf("Found it\n");
        }
        if (StringUtils.isNotBlank(testConfig.getOutputBomMustContainExternalSystemTypeId())) {
            System.out.printf("Looking for component with externalSystemTypeId: %s\n", testConfig.getOutputBomMustContainExternalSystemTypeId());
            boolean externalSystemTypeIdFound = false;
            for (int i = 0; i < doc.components.size(); i++) {
                System.out.printf("\tComponent: %s / %s; externalSystemTypeId: %s\n", doc.components.get(i).name, doc.components.get(i).version, doc.components.get(i).bdioExternalIdentifier.forge);
                if (doc.components.get(i).bdioExternalIdentifier.forge.equals(testConfig.getOutputBomMustContainExternalSystemTypeId())) {
                    externalSystemTypeIdFound = true;
                    break;
                }
            }
            assertTrue(externalSystemTypeIdFound);
            System.out.printf("Found it\n");
        }
        if (testConfig.getMode() != TestConfig.Mode.DETECT) {
            assertTrue(outputContainerFileSystemFile.exists());
        }
        if ((testConfig.getMinContainerFileSystemFileSize() > 0) || (testConfig.getMaxContainerFileSystemFileSize() > 0)) {
            final long actualContainerFileSystemFileSize = outputContainerFileSystemFile.length();
            assertTrue(actualContainerFileSystemFileSize >= testConfig.getMinContainerFileSystemFileSize());
            assertTrue(actualContainerFileSystemFileSize <= testConfig.getMaxContainerFileSystemFileSize());
        }
    }

    private static List<String> createCmd(final Random random, final ProgramVersion programVersion, final TestConfig.Mode mode, final String detectJarPath, final String inspectTargetArg, final String repo, final String tag,
        final String codelocationName, final List<String> additionalArgs)
        throws IOException {
        if (mode == TestConfig.Mode.DETECT) {
            return createDetectCmd(programVersion, mode, detectJarPath, inspectTargetArg, repo, tag, codelocationName, additionalArgs);
        } else {
            return createDockerInspectorCmd(random, programVersion, mode, inspectTargetArg, repo, tag, codelocationName, additionalArgs);
        }
    }

    private static List<String> createDetectCmd(final ProgramVersion programVersion, final TestConfig.Mode mode, final String detectJarPath, final String inspectTargetArg, final String repo, final String tag,
        final String codelocationName, final List<String> additionalArgs)
        throws IOException {
        if (StringUtils.isBlank(detectJarPath)) {
            throw new UnsupportedOperationException("Detect jar path must be provided");
        }
        final List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-jar");
        cmd.add(detectJarPath);
        cmd.add(String.format("--detect.docker.inspector.path=build/libs/blackduck-docker-inspector-%s.jar", programVersion.getProgramVersion()));
        cmd.add("--blackduck.offline.mode=true");
        cmd.add(String.format("--detect.docker.passthrough.blackduck.codelocation.name=%s", codelocationName));
        cmd.add("--detect.blackduck.signature.scanner.disabled=true");
        if (repo != null) {
            cmd.add(String.format("--detect.docker.passthrough.docker.image.repo=%s", repo));
        }
        if (tag != null) {
            cmd.add(String.format("--detect.docker.passthrough.docker.image.tag=%s", tag));
        }
        cmd.add("--logging.level.com.blackducksoftware.integration=DEBUG");
        cmd.add("--detect.excluded.bom.tool.types=gradle");

        final String adjustedTargetArg = inspectTargetArg.replace("--docker.", "--detect.docker.");
        cmd.add(adjustedTargetArg);

        if (additionalArgs != null) {
            for (final String additionalArg : additionalArgs) {
                final String adjustedArg = additionalArg.replace("--", "--detect.docker.passthrough.");
                cmd.add(adjustedArg);
            }
        }
        return cmd;
    }

    private static List<String> createDockerInspectorCmd(final Random random, final ProgramVersion programVersion, final TestConfig.Mode mode, final String inspectTargetArg, final String repo, final String tag,
        final String codelocationName, final List<String> additionalArgs) throws IOException {
        final List<String> cmd = new ArrayList<>();
        if (random.nextBoolean()) {
            cmd.add("build/blackduck-docker-inspector.sh");
            cmd.add(String.format("--jar.path=build/libs/blackduck-docker-inspector-%s.jar", programVersion.getProgramVersion()));
        } else {
            cmd.add("java");
            cmd.add("-jar");
            cmd.add(String.format("build/libs/blackduck-docker-inspector-%s.jar", programVersion.getProgramVersion()));
        }
        cmd.add("--upload.bdio=false");
        cmd.add(String.format("--blackduck.codelocation.name=%s", codelocationName));
        cmd.add(String.format("--output.path=%s/output", TestUtils.TEST_DIR_REL_PATH));
        cmd.add("--output.include.containerfilesystem=true");
        if (repo != null) {
            cmd.add(String.format("--docker.image.repo=%s", repo));
        }
        if (tag != null) {
            cmd.add(String.format("--docker.image.tag=%s", tag));
        }
        cmd.add("--logging.level.com.synopsys=DEBUG");
        if (mode == TestConfig.Mode.SPECIFY_II_DETAILS) {
            // --imageinspector.service.start=true is left to default (true)
            cmd.add(String.format("--imageinspector.service.port.alpine=%d", START_AS_NEEDED_IMAGE_INSPECTOR_PORT_ON_HOST_ALPINE));
            cmd.add(String.format("--imageinspector.service.port.centos=%d", START_AS_NEEDED_IMAGE_INSPECTOR_PORT_ON_HOST_CENTOS));
            cmd.add(String.format("--imageinspector.service.port.ubuntu=%d", START_AS_NEEDED_IMAGE_INSPECTOR_PORT_ON_HOST_UBUNTU));
            cmd.add(String.format("--shared.dir.path.local=%s/containerShared", TestUtils.TEST_DIR_REL_PATH));
        } else if (mode == TestConfig.Mode.NO_SERVICE_START) {
            cmd.add("--imageinspector.service.start=false");
            final File workingDir = new File(String.format("%s/endToEnd", TestUtils.TEST_DIR_REL_PATH));
            TestUtils.deleteDirIfExists(workingDir);
            cmd.add(String.format("--working.dir.path=%s", workingDir.getAbsolutePath()));
        } else if (mode == TestConfig.Mode.DEFAULT) {
            // Proceed with defaults (mostly)
            cmd.add(String.format("--shared.dir.path.local=%s/containerShared", TestUtils.TEST_DIR_REL_PATH));
        } else {
            throw new UnsupportedOperationException(String.format("Unexpected mode: %s", mode.toString()));
        }
        cmd.add(inspectTargetArg);
        if (additionalArgs != null) {
            cmd.addAll(additionalArgs);
        }
        return cmd;
    }

    public static void testTar(final Random random, final ProgramVersion programVersion, final String detectJarPath,
        final TestConfig testConfig)
        throws IOException, InterruptedException, IntegrationException {

        final File targetTarFile;
        if (testConfig.getTargetTarInSharedDir() != null) {
            targetTarFile = testConfig.getTargetTarInSharedDir();
        } else {
            targetTarFile = new File(testConfig.getTarFilePath());
        }

        final String inspectTargetArg = String.format("--docker.tar=%s", targetTarFile);
        ensureFileDoesNotExist(testConfig.getOutputContainerFileSystemFile());
        if (testConfig.getOutputSquashedImageFile() != null) {
            ensureFileDoesNotExist(testConfig.getOutputSquashedImageFile());
        }

        final File actualBdio;
        if (testConfig.getMode() == TestConfig.Mode.DETECT) {
            actualBdio = new File(String.format(String.format("%s/blackduck/bdio/%s_bdio.jsonld", System.getProperty("user.home"), testConfig.getCodelocationName())));
        } else {
            actualBdio = new File(String.format(String.format("%s/output/%s_bdio.jsonld", TestUtils.TEST_DIR_REL_PATH, testConfig.getCodelocationName())));
        }
        ensureFileDoesNotExist(actualBdio);

        final List<String> cmd = createCmd(random, programVersion, testConfig.getMode(), detectJarPath, inspectTargetArg, testConfig.getTargetRepo(), testConfig.getTargetTag(), testConfig.getCodelocationName(),
            testConfig.getAdditionalArgs());
        System.out.println(String.format("Running end to end test on %s with command %s", targetTarFile, cmd.toString()));
        TestUtils.execCmd(String.join(" ", cmd), 240000L, true, testConfig.getEnv());
        System.out.println("blackduck-docker-inspector done; verifying results...");
        System.out.printf("Expecting output BDIO file: %s\n", actualBdio.getAbsolutePath());
        assertTrue(String.format("%s does not exist", actualBdio.getAbsolutePath()), actualBdio.exists());
        if (testConfig.isRequireBdioMatch()) {
            final File expectedBdio = new File(
                String.format(String.format("src/test/resources/bdio/%s_bdio.jsonld", testConfig.getCodelocationName())));
            final List<String> exceptLinesContainingThese = new ArrayList<>();
            exceptLinesContainingThese.add("\"@id\":");
            exceptLinesContainingThese.add("spdx:name");
            exceptLinesContainingThese.add("spdx:created");
            exceptLinesContainingThese.add("Tool:");
            final boolean outputBdioMatches = TestUtils.contentEquals(expectedBdio, actualBdio, exceptLinesContainingThese);
            assertTrue("BDIO produced does not match expected BDIO", outputBdioMatches);
        }

        if (testConfig.getMode() != TestConfig.Mode.DETECT) {
            assertTrue(String.format("%s does not exist", testConfig.getOutputContainerFileSystemFile().getAbsolutePath()), testConfig.getOutputContainerFileSystemFile().exists());
        }
        if (testConfig.getOutputSquashedImageFile() != null) {
            assertTrue(String.format("%s does not exist", testConfig.getOutputSquashedImageFile().getAbsolutePath()), testConfig.getOutputSquashedImageFile().exists());
        }
        if ((testConfig.getMinContainerFileSystemFileSize() > 0) || (testConfig.getMaxContainerFileSystemFileSize() > 0)) {
            final long actualContainerFileSystemFileSize = testConfig.getOutputContainerFileSystemFile().length();
            assertTrue(actualContainerFileSystemFileSize >= testConfig.getMinContainerFileSystemFileSize());
            assertTrue(actualContainerFileSystemFileSize <= testConfig.getMaxContainerFileSystemFileSize());
        }
    }

    public static File getOutputContainerFileSystemFileFromTarFilename(final String tarFilename) {
        final String path = String.format("%s/output/%s", TestUtils.TEST_DIR_REL_PATH, Names.getContainerFileSystemTarFilename(null, tarFilename));
        System.out.println(String.format("Expecting output container filesystem file at: %s", path));
        return new File(path);
    }

    private static SimpleBdioDocument createBdioDocumentFromFile(final File bdioFile) throws IOException {
        final InputStream reader = new ByteArrayInputStream(FileUtils.readFileToByteArray(bdioFile));
        SimpleBdioDocument doc = null;
        try (BdioReader bdioReader = new BdioReader(new Gson(), reader)) {
            doc = bdioReader.readSimpleBdioDocument();
            return doc;
        }
    }

    private static File getOutputContainerFileSystemFileFromImageSpec(final String imageNameTag) {
        final String path = String.format("%s/output/%s", TestUtils.TEST_DIR_REL_PATH, Names.getContainerFileSystemTarFilename(imageNameTag, null));
        System.out.println(String.format("Expecting output container filesystem file at: %s", path));
        return new File(path);
    }

    private static void ensureFileDoesNotExist(final File outputContainerFileSystemFile) throws IOException {
        Files.deleteIfExists(outputContainerFileSystemFile.toPath());
        assertFalse(outputContainerFileSystemFile.exists());
    }
}