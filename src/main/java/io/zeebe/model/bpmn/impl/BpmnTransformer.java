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

import static io.zeebe.msgpack.mapping.Mapping.JSON_ROOT_PATH;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import java.util.*;
import java.util.stream.Collectors;

import io.zeebe.model.bpmn.BpmnAspect;
import io.zeebe.model.bpmn.impl.instance.*;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.impl.metadata.*;
import io.zeebe.model.bpmn.instance.*;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionFactory;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.spec.MsgPackWriter;
import org.agrona.*;
import org.agrona.concurrent.UnsafeBuffer;

public class BpmnTransformer
{
    private static final int INITIAL_SIZE_KEY_VALUE_PAIR = 128;

    private final MsgPackWriter msgPackWriter = new MsgPackWriter();

    public WorkflowDefinition transform(DefinitionsImpl definitions)
    {
        final Map<DirectBuffer, Workflow> workflowsById = new HashMap<>();

        final List<ProcessImpl> processes = definitions.getProcesses();
        for (int p = 0; p < processes.size(); p++)
        {
            final ProcessImpl process = processes.get(p);

            transformProcess(process);

            workflowsById.put(process.getBpmnProcessId(), process);
        }

        definitions.getWorkflowsById().putAll(workflowsById);

        return definitions;
    }

    private void transformProcess(final ProcessImpl process)
    {
        final List<FlowElementImpl> flowElements = collectFlowElements(process);
        process.getFlowElements().addAll(flowElements);

        final Map<DirectBuffer, FlowElementImpl> flowElementsById = getFlowElementsById(flowElements);
        process.getFlowElementMap().putAll(flowElementsById);

        setInitialStartEvent(process);

        transformSequenceFlows(process.getSequenceFlows(), flowElementsById);

        transformServiceTasks(process.getServiceTasks());

        transformExclusiveGateways(process.getExclusiveGateways());

        addBpmnAspects(process);
    }

    private List<FlowElementImpl> collectFlowElements(final ProcessImpl process)
    {
        final List<FlowElementImpl> flowElements = new ArrayList<>();
        flowElements.addAll(process.getStartEvents());
        flowElements.addAll(process.getEndEvents());
        flowElements.addAll(process.getSequenceFlows());
        flowElements.addAll(process.getServiceTasks());
        flowElements.addAll(process.getExclusiveGateways());
        return flowElements;
    }

    private Map<DirectBuffer, FlowElementImpl> getFlowElementsById(List<FlowElementImpl> flowElements)
    {
        final Map<DirectBuffer, FlowElementImpl> map = new HashMap<>();

        for (FlowElementImpl flowElement : flowElements)
        {
            map.put(flowElement.getIdAsBuffer(), flowElement);
        }

        return map;
    }

    private void setInitialStartEvent(final ProcessImpl process)
    {
        final List<StartEventImpl> startEvents = process.getStartEvents();
        if (startEvents.size() >= 1)
        {
            final StartEventImpl startEvent = startEvents.get(0);
            process.setInitialStartEvent(startEvent);
        }
    }

    private void transformSequenceFlows(final List<SequenceFlowImpl> sequenceFlows, final Map<DirectBuffer, FlowElementImpl> flowElementsById)
    {
        for (int s = 0; s < sequenceFlows.size(); s++)
        {
            final SequenceFlowImpl sequenceFlow = sequenceFlows.get(s);

            final FlowElementImpl sourceElement = flowElementsById.get(sequenceFlow.getSourceRefAsBuffer());
            if (sourceElement != null)
            {
                sequenceFlow.setSourceNode((FlowNodeImpl) sourceElement);
            }

            final FlowElementImpl targetElement = flowElementsById.get(sequenceFlow.getTargetRefAsBuffer());
            if (targetElement != null)
            {
                sequenceFlow.setTargetNode((FlowNodeImpl) targetElement);
            }

            if (sequenceFlow.hasCondition())
            {
                createCondition(sequenceFlow.getConditionExpression());
            }
        }
    }

    private void createCondition(ConditionExpressionImpl conditionExpression)
    {
        final CompiledJsonCondition condition = JsonConditionFactory.createCondition(conditionExpression.getText());

        conditionExpression.setCondition(condition);
    }

    private void transformServiceTasks(List<ServiceTaskImpl> serviceTasks)
    {
        for (int s = 0; s < serviceTasks.size(); s++)
        {
            final ServiceTaskImpl serviceTaskImpl = serviceTasks.get(s);

            ExtensionElementsImpl extensionElements = serviceTaskImpl.getExtensionElements();
            if (extensionElements == null)
            {
                extensionElements = new ExtensionElementsImpl();
                serviceTaskImpl.setExtensionElements(extensionElements);
            }

            TaskHeadersImpl taskHeaders = extensionElements.getTaskHeaders();
            if (taskHeaders == null)
            {
                taskHeaders = new TaskHeadersImpl();
                extensionElements.setTaskHeaders(taskHeaders);
            }
            transformTaskHeaders(taskHeaders);

            InputOutputMappingImpl inputOutputMapping = serviceTaskImpl.getInputOutputMapping();
            if (inputOutputMapping == null)
            {
                inputOutputMapping = new InputOutputMappingImpl();
                extensionElements.setInputOutputMapping(inputOutputMapping);
            }
            transformInputOutputMappings(inputOutputMapping);
        }
    }

    private void transformTaskHeaders(TaskHeadersImpl taskHeaders)
    {
        final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

        final List<TaskHeaderImpl> headers = taskHeaders.getTaskHeaders();

        if (!headers.isEmpty())
        {
            final ExpandableArrayBuffer expandableBuffer = new ExpandableArrayBuffer(INITIAL_SIZE_KEY_VALUE_PAIR * headers.size());
            msgPackWriter.wrap(expandableBuffer, 0);
            msgPackWriter.writeMapHeader(headers.size());

            for (int h = 0; h < headers.size(); h++)
            {
                final TaskHeaderImpl header = headers.get(h);

                final DirectBuffer key = wrapString(header.getKey());
                msgPackWriter.writeString(key);

                final DirectBuffer value = wrapString(header.getValue());
                msgPackWriter.writeString(value);
            }

            buffer.wrap(expandableBuffer.byteArray(), 0, msgPackWriter.getOffset());
        }

        taskHeaders.setEncodedMsgpack(buffer);
    }

    private void transformInputOutputMappings(InputOutputMappingImpl inputOutputMapping)
    {
        final Mapping[] inputMappings = createMappings(inputOutputMapping.getInputs());
        inputOutputMapping.setInputMappings(inputMappings);

        final Mapping[] outputMappings = createMappings(inputOutputMapping.getOutputs());
        inputOutputMapping.setOutputMappings(outputMappings);
    }

    private Mapping[] createMappings(final List<MappingImpl> mappings)
    {
        final Mapping[] map;

        if (mappings.size() == 1 && !isRootMapping(mappings.get(0)))
        {
            map = new Mapping[] { createMapping(mappings.get(0)) };
        }
        else if (mappings.size() > 1)
        {
            map = new Mapping[mappings.size()];

            for (int i = 0; i < mappings.size(); i++)
            {
                map[i] = createMapping(mappings.get(i));
            }
        }
        else
        {
            map = new Mapping[0];
        }

        return map;
    }

    private boolean isRootMapping(MappingImpl mapping)
    {
        return mapping.getSource().equals(JSON_ROOT_PATH) && mapping.getTarget().equals(JSON_ROOT_PATH);
    }

    private Mapping createMapping(MappingImpl mapping)
    {
        // TODO make JSON path compiler re-usable!
        final JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
        final JsonPathQuery query = queryCompiler.compile(mapping.getSource());

        return new Mapping(query, wrapString(mapping.getTarget()));
    }

    private void transformExclusiveGateways(List<ExclusiveGatewayImpl> exclusiveGateways)
    {
        for (int e = 0; e < exclusiveGateways.size(); e++)
        {
            final ExclusiveGatewayImpl exclusiveGateway = exclusiveGateways.get(e);

            final List<SequenceFlow> sequenceFlowsWithConditions = exclusiveGateway.getOutgoing().stream()
                    .filter(SequenceFlowImpl::hasCondition)
                    .collect(Collectors.toList());

            exclusiveGateway.setOutgoingSequenceFlowsWithConditions(sequenceFlowsWithConditions);
        }
    }

    private void addBpmnAspects(ProcessImpl process)
    {
        final List<FlowElement> flowElements = process.getFlowElements();
        for (int f = 0; f < flowElements.size(); f++)
        {
            final FlowElementImpl flowElement = (FlowElementImpl) flowElements.get(f);

            if (flowElement instanceof FlowNode)
            {
                final FlowNode flowNode = (FlowNode) flowElement;

                final List<SequenceFlow> outgoingSequenceFlows = flowNode.getOutgoingSequenceFlows();
                if (outgoingSequenceFlows.isEmpty())
                {
                    flowElement.setBpmnAspect(BpmnAspect.CONSUME_TOKEN);
                }
                else if (outgoingSequenceFlows.size() == 1 && !outgoingSequenceFlows.get(0).hasCondition())
                {
                    flowElement.setBpmnAspect(BpmnAspect.TAKE_SEQUENCE_FLOW);
                }
                else if (flowElement instanceof ExclusiveGateway)
                {
                    flowElement.setBpmnAspect(BpmnAspect.EXCLUSIVE_SPLIT);
                }
            }
        }
    }

}
