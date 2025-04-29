package myPackage;

import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class SimulazioneService {
    public String run(String mode) throws IOException {
        Main.main(new String[]{mode});
        return "Simulazione [" + mode + "] completata";
    }
}
