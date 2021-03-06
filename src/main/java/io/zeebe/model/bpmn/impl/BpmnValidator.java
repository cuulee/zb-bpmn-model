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
package io.zeebe.model.bpmn.impl;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.zeebe.model.bpmn.*;
import io.zeebe.model.bpmn.instance.*;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.mapping.Mapping;
import org.agrona.DirectBuffer;

public class BpmnValidator
{
    private static final String PROHIBITED_EXPRESSIONS_REGEX = "(\\.\\*)|(\\[.*,.*\\])";
    private static final Pattern PROHIBITED_EXPRESSIONS = Pattern.compile(PROHIBITED_EXPRESSIONS_REGEX);

    public ValidationResult validate(WorkflowDefinition definition)
    {
        final ValidationResultImpl validationResult = new ValidationResultImpl();

        final List<Workflow> executableWorkflows = definition.getWorkflows().stream()
                .filter(Workflow::isExecutable)
                .collect(Collectors.toList());

        if (executableWorkflows.isEmpty())
        {
            validationResult.addError(definition, "BPMN model must contain at least one executable process.");
        }

        for (Workflow executableWorkflow : executableWorkflows)
        {
            validateWorkflow(validationResult, executableWorkflow);
        }

        return validationResult;
    }

    private void validateWorkflow(ValidationResultImpl validationResult, Workflow workflow)
    {
        final DirectBuffer bpmnProcessId = workflow.getBpmnProcessId();
        if (bpmnProcessId == null || bpmnProcessId.capacity() == 0)
        {
            validationResult.addError(workflow, "BPMN process id is required.");
        }
        else if (bpmnProcessId.capacity() > ZeebeConstraints.ID_MAX_LENGTH)
        {
            validationResult.addError(workflow, String.format("BPMN process id must not be longer than %d.", ZeebeConstraints.ID_MAX_LENGTH));
        }

        if (workflow.getInitialStartEvent() == null)
        {
            validationResult.addError(workflow, "The process must contain at least one none start event.");
        }

        for (FlowElement flowElement : workflow.getFlowElements())
        {
            validateFlowElement(validationResult, flowElement);
        }
    }

    private void validateFlowElement(ValidationResultImpl validationResult, FlowElement flowElement)
    {
        final DirectBuffer id = flowElement.getIdAsBuffer();
        if (id == null || id.capacity() == 0)
        {
            validationResult.addError(flowElement, "Activity id is required.");
        }
        else if (id.capacity() > ZeebeConstraints.ID_MAX_LENGTH)
        {
            validationResult.addError(flowElement, String.format("Activity id must not be longer than %d.", ZeebeConstraints.ID_MAX_LENGTH));
        }

        if (flowElement instanceof FlowNode)
        {
            validateFlowNode(validationResult, (FlowNode) flowElement);
        }

        if (flowElement instanceof ServiceTask)
        {
            validateServiceTask(validationResult, (ServiceTask) flowElement);
        }
        else if (flowElement instanceof EndEvent)
        {
            validateEndEvent(validationResult, (EndEvent) flowElement);
        }
        else if (flowElement instanceof ExclusiveGateway)
        {
            validateExclusiveGateway(validationResult, (ExclusiveGateway) flowElement);
        }
    }

    private void validateFlowNode(ValidationResultImpl validationResult, FlowNode flowNode)
    {
        if (!(flowNode instanceof ExclusiveGateway))
        {
            if (flowNode.getOutgoingSequenceFlows().size() > 1)
            {
                validationResult.addError(flowNode, "The flow element must not have more than one outgoing sequence flow.");
            }
        }

        flowNode.getIncomingSequenceFlows().stream().filter(s -> s.getSourceNode() == null).forEach(s ->
        {
            validationResult.addError(s, "Cannot find source of sequence flow.");
        });

        flowNode.getOutgoingSequenceFlows().stream().filter(s -> s.getTargetNode() == null).forEach(s ->
        {
            validationResult.addError(s, "Cannot find target of sequence flow.");
        });
    }

    private void validateServiceTask(ValidationResultImpl validationResult, ServiceTask serviceTask)
    {
        final TaskDefinition taskDefinition = serviceTask.getTaskDefinition();
        if (taskDefinition == null)
        {
            validationResult.addError(serviceTask, String.format("A service task must contain a '%s' extension element.", BpmnConstants.ZEEBE_ELEMENT_TASK_DEFINITION));
        }
        else
        {
            validateTaskDefinition(validationResult, taskDefinition);
        }

        final TaskHeaders taskHeaders = serviceTask.getTaskHeaders();
        if (taskHeaders != null)
        {
            validateTaskHeaders(validationResult, taskHeaders);
        }

        final InputOutputMapping inputOutputMapping = serviceTask.getInputOutputMapping();
        if (inputOutputMapping != null)
        {
            validateInputOutputMapping(validationResult, inputOutputMapping);
        }
    }

    private void validateTaskDefinition(ValidationResultImpl validationResult, final TaskDefinition taskDefinition)
    {
        final DirectBuffer taskType = taskDefinition.getTypeAsBuffer();
        if (taskType == null || taskType.capacity() == 0)
        {
            validationResult.addError(taskDefinition, String.format("A task definition must contain a '%s' attribute which specifies the type of the task.", BpmnConstants.ZEEBE_ATTRIBUTE_TASK_TYPE));
        }

        final int retries = taskDefinition.getRetries();
        if (retries < 1)
        {
            validationResult.addError(taskDefinition, "The task retries must be greater than 0.");
        }
    }

    private void validateTaskHeaders(ValidationResultImpl validationResult, TaskHeaders taskHeader)
    {
        for (Entry<String, String> header : taskHeader.asMap().entrySet())
        {
            if (header.getKey() == null)
            {
                validationResult.addError(taskHeader, String.format("A task header must contain a '%s' attribute.", BpmnConstants.ZEEBE_ATTRIBUTE_TASK_HEADER_KEY));
            }

            if (header.getValue() == null)
            {
                validationResult.addError(taskHeader, String.format("A task header must contain a '%s' attribute.", BpmnConstants.ZEEBE_ATTRIBUTE_TASK_HEADER_VALUE));
            }
        }
    }

    private void validateInputOutputMapping(ValidationResultImpl validationResult, InputOutputMapping inputOutputMapping)
    {
        validateMappingExpressions(validationResult, inputOutputMapping, inputOutputMapping.getInputMappingsAsMap());
        validateMappingExpressions(validationResult, inputOutputMapping, inputOutputMapping.getOutputMappingsAsMap());

        validateMappings(validationResult, inputOutputMapping, inputOutputMapping.getInputMappings());
        validateMappings(validationResult, inputOutputMapping, inputOutputMapping.getOutputMappings());
    }

    private void validateMappingExpressions(ValidationResultImpl validationResult, InputOutputMapping element, Map<String, String> mappings)
    {
        for (Entry<String, String> mapping : mappings.entrySet())
        {
            final String source = mapping.getKey();
            final String target = mapping.getValue();

            if (PROHIBITED_EXPRESSIONS.matcher(source).find())
            {
                validationResult.addError(element, String.format("Source mapping: JSON path '%s' contains prohibited expression (for example $.* or $.(foo|bar)).", source));
            }

            if (PROHIBITED_EXPRESSIONS.matcher(target).find())
            {
                validationResult.addError(element, String.format("Target mapping: JSON path '%s' contains prohibited expression (for example $.* or $.(foo|bar)).", target));
            }

            if (mappings.size() > 1 && target.equals(Mapping.JSON_ROOT_PATH))
            {
                validationResult.addError(element, "Target mapping: root mapping is not allowed because it would override other mapping.");
            }
        }
    }

    private void validateMappings(ValidationResultImpl validationResult, InputOutputMapping element, Mapping[] mappings)
    {
        for (Mapping mapping : mappings)
        {
            final JsonPathQuery query = mapping.getSource();
            if (!query.isValid())
            {
                validationResult.addError(element, String.format("JSON path query '%s' is not valid! Reason: %s", bufferAsString(query.getExpression()), query.getErrorReason()));
            }
        }
    }

    private void validateEndEvent(ValidationResultImpl validationResult, EndEvent endEvent)
    {
        if (!endEvent.getOutgoingSequenceFlows().isEmpty())
        {
            validationResult.addError(endEvent, "An end event must not have an outgoing sequence flow.");
        }
    }

    private void validateExclusiveGateway(ValidationResultImpl validationResult, ExclusiveGateway exclusiveGateway)
    {
        if (exclusiveGateway.getBpmnAspect() == BpmnAspect.EXCLUSIVE_SPLIT)
        {
            final SequenceFlow defaultFlow = exclusiveGateway.getDefaultFlow();
            if (defaultFlow != null)
            {
                if (defaultFlow.hasCondition())
                {
                    validationResult.addError(defaultFlow, "A default sequence flow must not have a condition.");
                }

                if (!exclusiveGateway.getOutgoingSequenceFlows().contains(defaultFlow))
                {
                    validationResult.addError(exclusiveGateway, "The default sequence flow must be an outgoing sequence flow of the exclusive gateway.");
                }
            }
            else
            {
                validationResult.addWarning(exclusiveGateway, "An exclusive gateway should have a default sequence flow without condition.");
            }

            for (SequenceFlow sequenceFlow : exclusiveGateway.getOutgoingSequenceFlowsWithConditions())
            {
                final CompiledJsonCondition condition = sequenceFlow.getCondition();
                if (!condition.isValid())
                {
                    validationResult.addError(sequenceFlow, String.format("The condition '%s' is not valid: %s", condition.getExpression(), condition.getErrorMessage()));
                }
            }

            for (SequenceFlow sequenceFlow : exclusiveGateway.getOutgoingSequenceFlows())
            {
                if (!sequenceFlow.hasCondition() && !sequenceFlow.equals(defaultFlow))
                {
                    validationResult.addError(sequenceFlow, "A sequence flow on an exclusive gateway must have a condition, if it is not the default flow.");
                }
            }
        }
        else
        {
            if (exclusiveGateway.getOutgoingSequenceFlows().size() > 1)
            {
                validationResult.addError(exclusiveGateway, "An exclusive gateway with more than one outgoing sequence flow must have conditions on the sequence flows.");
            }
        }
    }

}
