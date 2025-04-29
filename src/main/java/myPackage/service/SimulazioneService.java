package myPackage.service;

import myPackage.Main;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class SimulazioneService {
    public String run(String mode, int rounds) throws IOException {
        Main.main(new String[]{mode, String.valueOf(rounds)});
        return "Simulazione [" + mode + "] completata";
    }
}
