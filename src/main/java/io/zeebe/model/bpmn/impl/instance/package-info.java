/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@XmlSchema(xmlns = {
        @XmlNs(namespaceURI = "http://camunda.org/schema/zeebe/1.0", prefix = "zeebe"),
        @XmlNs(namespaceURI = "http://www.omg.org/spec/BPMN/20100524/MODEL", prefix = "bpmn")
    },
    elementFormDefault = javax.xml.bind.annotation.XmlNsForm.QUALIFIED)

package io.zeebe.model.bpmn.impl.instance;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlSchema;
