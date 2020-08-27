/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.multitenant;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.redhat.che.multitenant.tenantdata.TenantDataProvider;
import com.redhat.che.multitenant.tenantdata.UserCheTenantData;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.openshift.api.model.Route;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.che.api.core.model.workspace.runtime.RuntimeIdentity;
import org.eclipse.che.api.workspace.server.spi.InfrastructureException;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.subject.Subject;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesEnvironment.PodData;
import org.eclipse.che.workspace.infrastructure.kubernetes.namespace.pvc.WorkspaceVolumesStrategy;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.AsyncStoragePodInterceptor;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.AsyncStorageProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.CertificateProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.GitConfigProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.ImagePullSecretProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.LogsVolumeMachineProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.NodeSelectorProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.PodTerminationGracePeriodProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.ProxySettingsProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.ServiceAccountProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.SshKeysProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.TlsProvisionerProvider;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.VcsSslCertificateProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.env.EnvVarsConverter;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.limits.ram.ContainerResourceProvisioner;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.restartpolicy.RestartPolicyRewriter;
import org.eclipse.che.workspace.infrastructure.kubernetes.provision.server.ServersConverter;
import org.eclipse.che.workspace.infrastructure.openshift.environment.OpenShiftEnvironment;
import org.eclipse.che.workspace.infrastructure.openshift.provision.OpenShiftUniqueNamesProvisioner;
import org.eclipse.che.workspace.infrastructure.openshift.provision.RouteTlsProvisioner;
import org.eclipse.che.workspace.infrastructure.openshift.server.OpenShiftPreviewUrlExposer;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class RhCheInfraEnvironmentProvisionerTest {
  private static final String WSAGENT_ROUTER_TIMEOUT = "10m";
  private static final String USER_ID = "userId";
  private static final String NAMESPACE = "project1";
  private static final String CLUSTER_URL = "https://api.starter-us-east-2.openshift.com/";
  private static final Subject SUBJECT = new SubjectImpl("name", USER_ID, "keycloakToken", false);
  private static final String OSO_TOKEN = "osoToken";

  @Mock private OpenShiftUniqueNamesProvisioner uniqueNamesProvisioner;
  @Mock private RouteTlsProvisioner tlsRouteProvisioner;
  @Mock private TlsProvisionerProvider<OpenShiftEnvironment> routeTlsProvisionerProvider;
  @Mock private ServersConverter<OpenShiftEnvironment> openShiftServersConverter;
  @Mock private EnvVarsConverter envVarsConverter;
  @Mock private RestartPolicyRewriter restartPolicyRewriter;
  @Mock private WorkspaceVolumesStrategy volumesStrategy;
  @Mock private ContainerResourceProvisioner containerResourceProvisioner;
  @Mock private LogsVolumeMachineProvisioner logsVolumeMachineProvisioner;
  @Mock private PodTerminationGracePeriodProvisioner podTerminationGracePeriodProvisioner;
  @Mock private ImagePullSecretProvisioner imagePullSecretProvisioner;
  @Mock private ProxySettingsProvisioner proxySettingsProvisioner;
  @Mock private NodeSelectorProvisioner nodeSelectorProvisioner;
  @Mock private AsyncStorageProvisioner asyncStorageProvisioner;
  @Mock private AsyncStoragePodInterceptor asyncStoragePodInterceptor;
  @Mock private CertificateProvisioner certificateProvisioner;
  @Mock private SshKeysProvisioner sshKeysProvisioner;
  @Mock private GitConfigProvisioner gitConfigProvisioner;
  @Mock private OpenShiftPreviewUrlExposer openshiftPreviewUrlExposer;

  @Mock private OpenshiftUserTokenProvider openshiftUserTokenProvider;
  @Mock private TenantDataProvider tenantDataProvider;
  @Mock private RuntimeIdentity runtimeIdentity;
  @Mock private OpenShiftEnvironment openShiftEnvironment;
  @Mock private ServiceAccountProvisioner serviceAccountProvisioner;
  @Mock private VcsSslCertificateProvisioner vcsSslCertificateProvisioner;

  private List<EnvVar> con1EnvVars;
  private List<EnvVar> con2EnvVars;
  private List<EnvVar> con3EnvVars;
  private Map<String, String> wsAgentRouteAnnotations;

  private RhCheInfraEnvironmentProvisioner provisioner;

  @BeforeMethod
  public void setUp() throws Exception {
    con1EnvVars = new ArrayList<>();
    con2EnvVars = new ArrayList<>();
    con3EnvVars = new ArrayList<>();
    when(routeTlsProvisionerProvider.get()).thenReturn(tlsRouteProvisioner);
    provisioner =
        new RhCheInfraEnvironmentProvisioner(
            true,
            uniqueNamesProvisioner,
            routeTlsProvisionerProvider,
            openShiftServersConverter,
            envVarsConverter,
            restartPolicyRewriter,
            volumesStrategy,
            containerResourceProvisioner,
            logsVolumeMachineProvisioner,
            openshiftUserTokenProvider,
            tenantDataProvider,
            podTerminationGracePeriodProvisioner,
            imagePullSecretProvisioner,
            proxySettingsProvisioner,
            nodeSelectorProvisioner,
            asyncStorageProvisioner,
            asyncStoragePodInterceptor,
            serviceAccountProvisioner,
            certificateProvisioner,
            sshKeysProvisioner,
            gitConfigProvisioner,
            openshiftPreviewUrlExposer,
            vcsSslCertificateProvisioner,
            false,
            WSAGENT_ROUTER_TIMEOUT);

    PodData podData1 = mock(PodData.class);
    PodData podData2 = mock(PodData.class);
    PodSpec podSpec1 = mock(PodSpec.class);
    PodSpec podSpec2 = mock(PodSpec.class);
    Container container1 = mock(Container.class);
    Container container2 = mock(Container.class);
    Container container3 = mock(Container.class);
    Route wsAgentRoute = mock(Route.class);
    ObjectMeta wsAgentRouteMetadata = mock(ObjectMeta.class);
    wsAgentRouteAnnotations = new HashMap<>();
    wsAgentRouteAnnotations.put("org.eclipse.che.server.wsagent/http.path", "/api");

    lenient().when(runtimeIdentity.getOwnerId()).thenReturn(USER_ID);
    when(openshiftUserTokenProvider.getToken(eq(SUBJECT))).thenReturn(OSO_TOKEN);
    when(tenantDataProvider.getUserCheTenantData(eq(SUBJECT), eq("user")))
        .thenReturn(new UserCheTenantData(NAMESPACE, CLUSTER_URL, null, false));
    when(openShiftEnvironment.getPodsData())
        .thenReturn(of("podData1", podData1, "podData2", podData2));
    when(openShiftEnvironment.getRoutes()).thenReturn(of("routeName", wsAgentRoute));
    when(podData1.getSpec()).thenReturn(podSpec1);
    when(podData2.getSpec()).thenReturn(podSpec2);
    when(podSpec1.getContainers()).thenReturn(singletonList(container1));
    when(podSpec2.getContainers()).thenReturn(asList(container2, container3));
    when(container1.getEnv()).thenReturn(con1EnvVars);
    when(container2.getEnv()).thenReturn(con2EnvVars);
    when(container3.getEnv()).thenReturn(con3EnvVars);
    when(wsAgentRoute.getMetadata()).thenReturn(wsAgentRouteMetadata);
    when(wsAgentRouteMetadata.getAnnotations()).thenReturn(wsAgentRouteAnnotations);

    EnvironmentContext.getCurrent().setSubject(SUBJECT);
  }

  @Test
  public void shouldSetEnvVars() throws Exception {
    provisioner.provision(openShiftEnvironment, runtimeIdentity);

    verifyOcLoginEnvVarsPresence(con1EnvVars);
    verifyOcLoginEnvVarsPresence(con2EnvVars);
    verifyOcLoginEnvVarsPresence(con3EnvVars);
    verify(tenantDataProvider).getUserCheTenantData(eq(SUBJECT), eq("user"));
  }

  @Test
  public void shouldAnnotateWsAgentRouteWithTimeout() throws Exception {
    provisioner.provision(openShiftEnvironment, runtimeIdentity);

    assertTrue(wsAgentRouteAnnotations.containsKey("haproxy.router.openshift.io/timeout"));
    assertEquals(
        wsAgentRouteAnnotations.get("haproxy.router.openshift.io/timeout"), WSAGENT_ROUTER_TIMEOUT);
  }

  @Test
  public void shouldReplaceEnvVarIfItIsPresent() throws Exception {
    con2EnvVars.add(new EnvVar(RhCheInfraEnvironmentProvisioner.TOKEN_VAR, "anotherValue", null));

    provisioner.provision(openShiftEnvironment, runtimeIdentity);

    assertEquals(con2EnvVars.size(), 4);
    verifyOcLoginEnvVarsPresence(con2EnvVars);
  }

  @Test
  public void shouldPreserveExistingEnvVars() throws Exception {
    con3EnvVars.add(new EnvVar("OTHER_VAR", "otherValue", null));

    provisioner.provision(openShiftEnvironment, runtimeIdentity);

    assertEquals(con3EnvVars.size(), 5);
    verifyOcLoginEnvVarsPresence(con3EnvVars);
    verifyEnvVarPresence("OTHER_VAR", "otherValue", con3EnvVars);
  }

  @Test
  public void shouldNotThrowExceptionIfTokenFetchingFails() throws Exception {
    doThrow(new InfrastructureException("error"))
        .when(openshiftUserTokenProvider)
        .getToken(eq(SUBJECT));

    provisioner.provision(openShiftEnvironment, runtimeIdentity);

    assertTrue(con1EnvVars.isEmpty());
    assertTrue(con2EnvVars.isEmpty());
    assertTrue(con3EnvVars.isEmpty());
  }

  @Test
  public void shouldNotThrowExceptionIfTokenIsNull() throws Exception {
    doReturn(null).when(openshiftUserTokenProvider).getToken(eq(SUBJECT));

    provisioner.provision(openShiftEnvironment, runtimeIdentity);

    assertTrue(con1EnvVars.isEmpty());
    assertTrue(con2EnvVars.isEmpty());
    assertTrue(con3EnvVars.isEmpty());
  }

  @Test
  public void shouldNotThrowExceptionIfTenantDataFetchingFails() throws Exception {
    doThrow(new InfrastructureException("error"))
        .when(tenantDataProvider)
        .getUserCheTenantData(eq(SUBJECT), eq("user"));

    provisioner.provision(openShiftEnvironment, runtimeIdentity);

    assertTrue(con1EnvVars.isEmpty());
    assertTrue(con2EnvVars.isEmpty());
    assertTrue(con3EnvVars.isEmpty());
    verify(tenantDataProvider).getUserCheTenantData(eq(SUBJECT), eq("user"));
  }

  private void verifyOcLoginEnvVarsPresence(List<EnvVar> envVars) {
    verifyEnvVarPresence(RhCheInfraEnvironmentProvisioner.CLUSTER_VAR, CLUSTER_URL, envVars);
    verifyEnvVarPresence(RhCheInfraEnvironmentProvisioner.PROJECT_VAR, NAMESPACE, envVars);
    verifyEnvVarPresence(RhCheInfraEnvironmentProvisioner.TOKEN_VAR, OSO_TOKEN, envVars);
  }

  private void verifyEnvVarPresence(String name, String value, List<EnvVar> envVars) {
    boolean match = false;
    for (EnvVar envVar : envVars) {
      if (envVar.getName().equals(name) && envVar.getValue().equals(value)) {
        match = true;
      }
    }
    assertTrue(match, "No env var with name '%s' and value '%s' found in env vars list");
  }
}
