/*
 * Copyright (C) 2017 Black Duck Software Inc.
 * http://www.blackducksoftware.com/
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Black Duck Software ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Black Duck Software.
 */
package com.blackducksoftware.integration.hub.docker.mock


import com.blackducksoftware.integration.hub.docker.executor.Executor

class ExecutorMock extends Executor {

    File resourceFile

    ExecutorMock(File resourceFile){
        this.resourceFile = resourceFile
    }

    String[] listPackages(){
        resourceFile as String[]
    }

    @Override
    public void init() {
    }
}