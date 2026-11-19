package lwj.code.server.api;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class UuidGenerator {

    public UUID nextUuid7(){

        return UuidCreator.getTimeOrderedEpoch();
    }
}
