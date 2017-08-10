package io.zeebe.model.bpmn.impl;

import static io.zeebe.msgpack.mapping.Mapping.JSON_ROOT_PATH;
import static io.zeebe.util.EnsureUtil.ensureGreaterThan;
import static io.zeebe.util.EnsureUtil.ensureNotNull;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import java.util.*;

import io.zeebe.model.bpmn.impl.instance.*;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.impl.metadata.*;
import io.zeebe.model.bpmn.instance.Workflow;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
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
        final Map<DirectBuffer, FlowElementImpl> flowElementsById = getFlowElementsById(process);
        process.getFlowElementMap().putAll(flowElementsById);

        setInitialStartEvent(process);

        linkSequenceFlows(process, flowElementsById);

        transformServiceTasks(process.getServiceTasks());
    }

    private Map<DirectBuffer, FlowElementImpl> getFlowElementsById(final ProcessImpl process)
    {
        final Map<DirectBuffer, FlowElementImpl> flowElementsById = new HashMap<>();

        final List<FlowElementImpl> flowElements = new ArrayList<>();
        flowElements.addAll(process.getStartEvents());
        flowElements.addAll(process.getEndEvents());
        flowElements.addAll(process.getSequenceFlows());
        flowElements.addAll(process.getServiceTasks());

        for (int f = 0; f < flowElements.size(); f++)
        {
            final FlowElementImpl flowElement = flowElements.get(f);

            flowElementsById.put(flowElement.getIdAsBuffer(), flowElement);
        }

        return flowElementsById;
    }

    private void setInitialStartEvent(final ProcessImpl process)
    {
        final List<StartEventImpl> startEvents = process.getStartEvents();
        ensureGreaterThan("start events", startEvents.size(), 0);

        final StartEventImpl startEvent = startEvents.get(0);
        ensureNotNull("start event", startEvent);

        process.setInitialStartEvent(startEvent);
    }

    private void linkSequenceFlows(final ProcessImpl process, final Map<DirectBuffer, FlowElementImpl> flowElementsById)
    {
        final List<SequenceFlowImpl> sequenceFlows = process.getSequenceFlows();
        for (int s = 0; s < sequenceFlows.size(); s++)
        {
            final SequenceFlowImpl sequenceFlow = sequenceFlows.get(s);

            final FlowElementImpl sourceElement = flowElementsById.get(sequenceFlow.getSourceRefAsBuffer());
            ensureNotNull("source element", sourceElement);
            sequenceFlow.setSourceNode((FlowNodeImpl) sourceElement);

            final FlowElementImpl targetElement = flowElementsById.get(sequenceFlow.getTargetRefAsBuffer());
            ensureNotNull("target element", targetElement);
            sequenceFlow.setTargetNode((FlowNodeImpl) targetElement);
        }
    }

    private void transformServiceTasks(List<ServiceTaskImpl> serviceTasks)
    {
        for (int s = 0; s < serviceTasks.size(); s++)
        {
            final ServiceTaskImpl serviceTaskImpl = serviceTasks.get(s);

            final TaskHeadersImpl taskHeaders = serviceTaskImpl.getTaskHeaders();
            if (taskHeaders != null)
            {
                transformTaskHeaders(taskHeaders);
            }

            final InputOutputMappingImpl inputOutputMapping = serviceTaskImpl.getInputOutputMapping();
            if (inputOutputMapping != null)
            {
                transformInputOutputMappings(inputOutputMapping);
            }
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
        final Mapping[] map = new Mapping[mappings.size()];

        if (mappings.size() == 1 && !isRootMapping(mappings.get(0)))
        {
            map[0] = createMapping(mappings.get(0));
        }
        else if (mappings.size() > 1)
        {
            for (int i = 0; i < mappings.size(); i++)
            {
                map[i] = createMapping(mappings.get(i));
            }
        }

        return map;
    }

    private boolean isRootMapping(MappingImpl mapping)
    {
        return mapping.getSource().equals(JSON_ROOT_PATH) && mapping.getTarget().equals(JSON_ROOT_PATH);
    }

    private Mapping createMapping(MappingImpl mapping)
    {
        //TODO make JSON path compiler re-usable!
        final JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
        final JsonPathQuery query = queryCompiler.compile(mapping.getSource());

        return new Mapping(query, mapping.getTarget());
    }

}