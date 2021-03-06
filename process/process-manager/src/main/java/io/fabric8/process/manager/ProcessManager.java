/*
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.process.manager;

import com.google.common.collect.ImmutableMap;

import java.util.List;

/**
 * Returns the service used to install and control managed processes
 */
public interface ProcessManager {

    /**
     * Returns the current installed processes which may or may not be running right now
     */
    List<Installation> listInstallations();

    /**
     * Installs a process with an optional URL of the controller JSON
     * together with an optional install step
     */
    Installation install(InstallOptions parameters, InstallTask postInstall) throws Exception;

    /**
     * Installs an executable jar as a new managed process
     */
    Installation installJar(InstallOptions parameters) throws Exception;

    ImmutableMap<Integer, Installation> listInstallationMap();
}
