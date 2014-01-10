/*
 * Copyright (C) FuseSource, Inc.
 *   http://fusesource.com
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package io.fabric8.docker.provider;


import io.fabric8.api.Container;
import io.fabric8.api.ContainerAutoScaler;
import io.fabric8.api.ContainerAutoScalerFactory;
import io.fabric8.api.ContainerProvider;
import io.fabric8.api.CreationStateListener;
import io.fabric8.api.FabricService;
import io.fabric8.api.NameValidator;
import io.fabric8.api.Profile;
import io.fabric8.api.Version;
import io.fabric8.api.jcip.ThreadSafe;
import io.fabric8.api.scr.AbstractComponent;
import io.fabric8.api.scr.ValidatingReference;
import io.fabric8.docker.api.Docker;
import io.fabric8.docker.api.DockerFactory;
import io.fabric8.docker.api.container.ContainerConfig;
import io.fabric8.docker.api.container.ContainerCreateStatus;
import io.fabric8.docker.api.container.HostConfig;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.fusesource.insight.log.support.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@ThreadSafe
@Component(name = "io.fabric8.container.provider.docker", label = "Fabric8 Docker Container Provider", policy = ConfigurationPolicy.OPTIONAL, immediate = true, metatype = true)
@Service(ContainerProvider.class)
public final class DockerContainerProvider extends AbstractComponent implements ContainerProvider<CreateDockerContainerOptions, CreateDockerContainerMetadata>, ContainerAutoScalerFactory {

    private static final transient Logger LOG = LoggerFactory.getLogger(DockerContainerProvider.class);

    private static final String SCHEME = "docker";

    @Reference(referenceInterface = FabricService.class)
    private final ValidatingReference<FabricService> fabricService = new ValidatingReference<FabricService>();

    @Reference(referenceInterface = MBeanServer.class)
    private MBeanServer mbeanServer;

    private ObjectName objectName;
    private DockerFacade mbean;
    private DockerFactory dockerFactory = new DockerFactory();
    private Docker docker;


    @Activate
    void activate(Map<String, ?> configuration) throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        updateConfiguration(configuration);
        activateComponent();
        if (mbeanServer != null) {
            objectName = new ObjectName("io.fabric8:type=OpenShift");
            mbean = new DockerFacade(this);
            if (!mbeanServer.isRegistered(objectName)) {
                mbeanServer.registerMBean(mbean, objectName);
            }
        }
    }

    @Modified
    void modified(Map<String, ?> configuration) {
        updateConfiguration(configuration);
    }

    @Deactivate
    void deactivate() throws MBeanRegistrationException, InstanceNotFoundException {
        if (mbeanServer != null) {
            if (mbeanServer.isRegistered(objectName)) {
                mbeanServer.unregisterMBean(objectName);
            }
        }
        deactivateComponent();
    }

    private void updateConfiguration(Map<String, ?> configuration) {
        Object url = configuration.get("url");
        if (url != null) {
            dockerFactory.setAddress(url.toString());
        }
        this.docker = dockerFactory.createDocker();
    }

    FabricService getFabricService() {
        return fabricService.get();
    }

    @Override
    public CreateDockerContainerOptions.Builder newBuilder() {
        return CreateDockerContainerOptions.builder();
    }

    @Override
    public CreateDockerContainerMetadata create(CreateDockerContainerOptions options, CreationStateListener listener) throws Exception {
        assertValid();

        ContainerConfig containerConfig = options.createContainerConfig();

        // allow values to be extracted from the profile configuration
        // such as the image
        Set<String> profiles = options.getProfiles();
        String versionId = options.getVersion();
        Map<String, String> configOverlay = new HashMap<String, String>();
        if (profiles != null && versionId != null) {
            Version version = fabricService.get().getVersion(versionId);
            if (version != null) {
                for (String profileId : profiles) {
                    Profile profile = version.getProfile(profileId);
                    if (profile != null) {
                        Profile overlay = profile.getOverlay();
                        Map<String, String> dockerConfig = overlay.getConfiguration(DockerConstants.DOCKER_PROVIDER_PID);
                        if (dockerConfig != null)  {
                            configOverlay.putAll(dockerConfig);
                        }
                    }
                }
            }
        }
        String image = containerConfig.getImage();
        if (Strings.isEmpty(image)) {
            image = configOverlay.get(DockerConstants.PROPERTIES.IMAGE);
            containerConfig.setImage(image);
        }
        LOG.info("Creating image: " + image);

        ContainerCreateStatus status = docker.containerCreate(containerConfig);
        CreateDockerContainerMetadata metadata = CreateDockerContainerMetadata.newInstance(containerConfig, status);
        metadata.setCreateOptions(options);
        return metadata;
    }

    @Override
    public void start(Container container) {
        assertValid();
        String id = getDockerContainerId(container);
        if (!Strings.isEmpty(id)) {
            HostConfig hostConfig = new HostConfig();
            // TODO populate host config?
            docker.containerStart(id, hostConfig);
        }
    }

    @Override
    public void stop(Container container) {
        assertValid();
        String id = getDockerContainerId(container);
        if (!Strings.isEmpty(id)) {
            Integer timeToWait = null;
            docker.containerStop(id, timeToWait);
        }
    }

    @Override
    public void destroy(Container container) {
        assertValid();
        String id = getDockerContainerId(container);
        if (!Strings.isEmpty(id)) {
            Integer removeVolumes = 1;
            docker.containerRemove(id, removeVolumes);
        }
    }

    @Override
    public String getScheme() {
        assertValid();
        return SCHEME;
    }

    @Override
    public Class<CreateDockerContainerOptions> getOptionsType() {
        assertValid();
        return CreateDockerContainerOptions.class;
    }

    @Override
    public Class<CreateDockerContainerMetadata> getMetadataType() {
        assertValid();
        return CreateDockerContainerMetadata.class;
    }

    public Docker getDocker() {
        return docker;
    }

    protected String getDockerContainerId(Container container) {
        // TODO we maybe want to store the ID inside the container config?
        return container.getId();
    }

    @Override
    public ContainerAutoScaler createAutoScaler() {
        return new DockerAutoScaler(this);
    }

    /**
     * Creates a name validator that checks there isn't an application of the given name already
     */
    NameValidator createNameValidator(CreateDockerContainerOptions options) {
        return new NameValidator() {
            @Override
            public boolean isValid(String name) {
                // TODO
                return true;
            }
        };
    }

    void bindFabricService(FabricService fabricService) {
        this.fabricService.bind(fabricService);
    }

    void unbindFabricService(FabricService fabricService) {
        this.fabricService.unbind(fabricService);
    }

    public String getDockerAddress() {
        return dockerFactory.getAddress();
    }
}