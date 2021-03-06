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
package io.zeebe.model.bpmn.impl.instance;

import io.zeebe.model.bpmn.impl.metadata.InputOutputMappingImpl;
import io.zeebe.model.bpmn.impl.metadata.TaskHeadersImpl;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.TaskDefinition;

public class ServiceTaskImpl extends FlowNodeImpl implements ServiceTask
{

    @Override
    public TaskDefinition getTaskDefinition()
    {
        return getExtensionElements() != null ? getExtensionElements().getTaskDefinition() : null;
    }

    @Override
    public TaskHeadersImpl getTaskHeaders()
    {
        return getExtensionElements() != null ? getExtensionElements().getTaskHeaders() : null;
    }

    @Override
    public InputOutputMappingImpl getInputOutputMapping()
    {
        return getExtensionElements() != null ? getExtensionElements().getInputOutputMapping() : null;
    }

}
