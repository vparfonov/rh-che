/*
 * Copyright (c) 2016-2018 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.che.plugin.analytics.wsmaster;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.commons.annotation.Nullable;

@Path("fabric8-che-analytics")
public class AnalyticsSettingsService extends Service {

  String segmentWriteKey;
  String woopraDomain;

  @Inject
  public AnalyticsSettingsService(
      @Nullable @Named("che.fabric8.analytics.segment_write_key") String segmentWriteKey,
      @Nullable @Named("che.fabric8.analytics.woopra_domain") String woopraDomain) {
    this.segmentWriteKey = segmentWriteKey;
    this.woopraDomain = woopraDomain;
  }

  @GET
  @Path("segment-write-key")
  public String segmentWriteKey() {
    return segmentWriteKey == null ? "" : segmentWriteKey;
  }

  @GET
  @Path("woopra-domain")
  public String woopraDomain() {
    return woopraDomain == null ? "" : woopraDomain;
  }
}
