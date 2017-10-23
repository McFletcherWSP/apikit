/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.apikit.validation.body.form;

import org.mule.test.runner.ArtifactClassLoaderRunnerConfig;

@ArtifactClassLoaderRunnerConfig(sharedRuntimeLibs = {"org.mule.tests:mule-tests-unit"})
public class MultipartFormFunctionalTestCaseRaml10 extends MultipartFormFunctionalTestCase {

  @Override
  protected String getConfigResources() {
    return "org/mule/module/apikit/validation/formParameters/mule-config-v2.xml";
  }
}
