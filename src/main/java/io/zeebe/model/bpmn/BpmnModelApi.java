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
package io.zeebe.model.bpmn;

import java.io.*;

import io.zeebe.model.bpmn.builder.BpmnBuilder;
import io.zeebe.model.bpmn.impl.*;
import io.zeebe.model.bpmn.impl.instance.DefinitionsImpl;
import io.zeebe.model.bpmn.impl.yaml.BpmnYamlParser;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.agrona.DirectBuffer;

public class BpmnModelApi
{

    private final BpmnParser parser = new BpmnParser();
    private final BpmnTransformer transformer = new BpmnTransformer();
    private final BpmnBuilder builder = new BpmnBuilder(transformer);
    private final BpmnYamlParser yamlParser = new BpmnYamlParser(builder);
    private final BpmnValidator validator = new BpmnValidator();

    public BpmnBuilder createExecutableWorkflow(String bpmnProcessId)
    {
        return builder.wrap(bpmnProcessId);
    }

    public WorkflowDefinition readFromFile(File file)
    {
        final DefinitionsImpl definitions = parser.readFromFile(file);
        final WorkflowDefinition workflowDefinition = transformer.transform(definitions);

        return workflowDefinition;
    }

    public WorkflowDefinition readFromStream(InputStream stream)
    {
        final DefinitionsImpl definitions = parser.readFromStream(stream);
        final WorkflowDefinition workflowDefinition = transformer.transform(definitions);

        return workflowDefinition;
    }

    public WorkflowDefinition readFromBuffer(DirectBuffer buffer)
    {
        final byte[] bytes = new byte[buffer.capacity()];
        buffer.getBytes(0, bytes);

        return readFromStream(new ByteArrayInputStream(bytes));
    }

    public WorkflowDefinition readFromString(String workflow)
    {
        return readFromStream(new ByteArrayInputStream(workflow.getBytes()));
    }

    public WorkflowDefinition readFromYamlFile(File file)
    {
        return yamlParser.readFromFile(file);
    }

    public WorkflowDefinition readFromYamlStream(InputStream stream)
    {
        return yamlParser.readFromStream(stream);
    }

    public ValidationResult validate(WorkflowDefinition definition)
    {
        return validator.validate(definition);
    }

    public String convertToString(WorkflowDefinition definition)
    {
        if (definition instanceof DefinitionsImpl)
        {
            return parser.convertToString((DefinitionsImpl) definition);
        }
        else
        {
            throw new RuntimeException("not supported");
        }
    }

}
