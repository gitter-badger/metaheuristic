package aiai.ai.launchpad.server;

import aiai.ai.Enums;
import aiai.ai.comm.Command;
import aiai.ai.comm.CommandProcessor;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Station;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.repositories.ExperimentRepository;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.StationsRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Profile("launchpad")
public class ServerService {

    private static final ExchangeData EXCHANGE_DATA_NOP = new ExchangeData(Protocol.NOP);

    private final CommandProcessor commandProcessor;
    private final StationsRepository stationsRepository;
    private final FlowInstanceRepository flowInstanceRepository;
    private final TaskRepository taskRepository;
    private final CommandSetter commandSetter;

    @Service
    @Profile("launchpad")
    public static class CommandSetter {
        private final FlowInstanceRepository flowInstanceRepository;

        public CommandSetter(FlowInstanceRepository flowInstanceRepository) {
            this.flowInstanceRepository = flowInstanceRepository;
        }

        @Transactional(readOnly = true)
        public void setCommandInTransaction(ExchangeData resultData) {
            try (Stream<FlowInstance> stream = flowInstanceRepository.findAllAsStream() ) {
                resultData.setCommand(new Protocol.FlowInstanceStatus(
                        stream.map(ServerService::to).collect(Collectors.toList())));
            }
        }
    }

    public ServerService(CommandProcessor commandProcessor, StationsRepository stationsRepository, ExperimentRepository experimentRepository, BinaryDataService binaryDataService, FlowInstanceRepository flowInstanceRepository, TaskRepository taskRepository, CommandSetter commandSetter) {
        this.commandProcessor = commandProcessor;
        this.stationsRepository = stationsRepository;
        this.flowInstanceRepository = flowInstanceRepository;
        this.taskRepository = taskRepository;
        this.commandSetter = commandSetter;
    }

    ExchangeData processRequest(@RequestBody ExchangeData data, String remoteAddress) {
        if (StringUtils.isBlank(data.getStationId())) {
            return new ExchangeData(commandProcessor.process(new Protocol.RequestStationId()));
        }
        if (stationsRepository.findById(Long.parseLong(data.getStationId())).orElse(null)==null) {
            Station s = new Station();
            s.setIp(remoteAddress);
            s.setDescription("Id was reassigned from "+data.getStationId());
            stationsRepository.save(s);
            return new ExchangeData(new Protocol.ReAssignStationId(s.getId()));
        }

        ExchangeData resultData = new ExchangeData();
        commandSetter.setCommandInTransaction(resultData);

        List<Command> commands = data.getCommands();
        for (Command command : commands) {
            if (data.getStationId()!=null && command instanceof Protocol.RequestStationId) {
                continue;
            }
            resultData.setCommands(commandProcessor.process(command));
        }

        return resultData.getCommands().isEmpty() ? EXCHANGE_DATA_NOP : resultData;
    }

    private static Protocol.FlowInstanceStatus.SimpleStatus to(FlowInstance flowInstance) {
        return new Protocol.FlowInstanceStatus.SimpleStatus(flowInstance.getId(), Enums.FlowInstanceExecState.toState(flowInstance.getExecState()));
    }

}
