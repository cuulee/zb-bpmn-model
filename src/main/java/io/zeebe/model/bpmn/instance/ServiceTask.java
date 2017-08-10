package io.zeebe.model.bpmn.instance;

public interface ServiceTask extends FlowNode
{

    TaskDefinition getTaskDefinition();

    TaskHeaders getTaskHeaders();

    InputOutputMapping getInputOutputMapping();

}