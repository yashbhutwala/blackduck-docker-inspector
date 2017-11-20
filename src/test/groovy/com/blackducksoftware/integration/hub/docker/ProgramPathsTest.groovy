package com.blackducksoftware.integration.hub.docker


import static org.junit.Assert.*

import org.junit.Test

class ProgramPathsTest {

    @Test
    public void testReleasedVersion() {
        doTest("hub-docker-1.0.0.jar", true)
    }

    @Test
    public void testSnapshotVersion() {
        doTest("hub-docker-0.0.1-SNAPSHOT.jar", false)
    }

    private void doTest(String jarFileName, boolean prefixCodeLocationName) {
        File installDir = TestUtils.createTempDirectory()
        String installDirPath = installDir.getAbsolutePath()
        File jarFile = new File(installDir, "hub-docker-1.0.0.jar")
        jarFile.createNewFile()

        ProgramPaths paths = [
            getQualifiedJarPath: { -> return "file:${installDirPath}/${jarFileName}OTHERJUNK".toString() }
        ] as ProgramPaths
        paths.setGivenJarPath("/tmp/t.tar");

        if (prefixCodeLocationName) {
            paths.codeLocationPrefix = "xyz"
        }

        paths.setHubDockerPgmDirPath(installDir.getAbsolutePath() + "/")
        paths.init()

        assertEquals("${installDirPath}/config/".toString(), paths.getHubDockerConfigDirPath())
        assertEquals("${installDirPath}/config/application.properties".toString(), paths.getHubDockerConfigFilePath())
        assertEquals("${installDirPath}/target/".toString(), paths.getHubDockerTargetDirPath())
        assertEquals("${installDirPath}/".toString(), paths.getHubDockerPgmDirPath())
        assertEquals("${installDirPath}/${jarFileName}".toString(), paths.getHubDockerJarPathActual())

        if (prefixCodeLocationName) {
            assertEquals("xyz_imageName_imageTag_pkgMgrFilePath_pkgMgrName", paths.getCodeLocationName("imageName", "imageTag",  "pkgMgrFilePath",  "pkgMgrName"))
        } else {
            assertEquals("imageName_imageTag_pkgMgrFilePath_pkgMgrName", paths.getCodeLocationName("imageName", "imageTag",  "pkgMgrFilePath",  "pkgMgrName"))
        }
    }
}