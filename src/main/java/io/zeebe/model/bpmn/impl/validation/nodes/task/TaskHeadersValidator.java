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
package io.zeebe.model.bpmn.impl.validation.nodes.task;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.impl.metadata.TaskHeadersImpl;
import io.zeebe.model.bpmn.impl.validation.ValidationResultImpl;

import java.util.Map;

public class TaskHeadersValidator
{
    public void validate(ValidationResultImpl validationResult, TaskHeadersImpl taskHeaders)
    {
        for (Map.Entry<String, String> header : taskHeaders.asMap().entrySet())
        {
            if (header.getKey() == null)
            {
                validationResult.addError(taskHeaders, String.format("A task header must contain a '%s' attribute.", BpmnConstants.ZEEBE_ATTRIBUTE_TASK_HEADER_KEY));
            }

            if (header.getValue() == null)
            {
                validationResult.addError(taskHeaders, String.format("A task header must contain a '%s' attribute.", BpmnConstants.ZEEBE_ATTRIBUTE_TASK_HEADER_VALUE));
            }
        }
    }
}
