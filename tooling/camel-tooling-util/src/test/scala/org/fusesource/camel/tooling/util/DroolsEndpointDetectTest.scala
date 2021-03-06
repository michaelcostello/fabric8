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
package org.fusesource.camel.tooling.util

import java.io.File
import org.junit.Assert._
import org.junit.Ignore

@Ignore
class DroolsEndpointDetectTest extends RouteXmlTestSupport {

  test("parses valid XML file") {
    val x = assertRoutes(new File(baseDir, "src/test/resources/drools-spring.xml"), 0)

    val uris = x.endpointUris
    println("Found uris: " + uris)

    expect(1, "expected one drools endpoint URI but got: " + uris){ uris.size }
    assertTrue(uris.contains("drools:node1/ksession1"))
  }

}