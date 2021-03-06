/**
 * blackduck-docker-inspector
 *
 * Copyright (c) 2020 Synopsys, Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.blackduck.dockerinspector.httpclient;

import java.io.IOException;
import java.net.MalformedURLException;

import com.synopsys.integration.blackduck.dockerinspector.output.ImageTarWrapper;
import com.synopsys.integration.exception.IntegrationException;

public interface ImageInspectorClient {

    ImageTarWrapper copyTarfileToSharedDir(final ImageTarWrapper givenDockerTarfile) throws IOException;

    String getBdio(String hostPathToTarFile, String containerPathToInputDockerTarfile, String givenImageRepo, String givenImageTag,
        String containerPathToOutputFileSystemFile, String containerFileSystemExcludedPaths,
        final boolean organizeComponentsByLayer, final boolean includeRemovedComponents, final boolean cleanup,
        final String platformTopLayerId, final String targetLinuxDistro)
        throws IntegrationException, MalformedURLException, InterruptedException;

    boolean isApplicable();
}
