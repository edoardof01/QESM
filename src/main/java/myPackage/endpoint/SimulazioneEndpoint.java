package myPackage.endpoint;

import myPackage.service.SimulazioneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimulazioneEndpoint {

    @Autowired
    private SimulazioneService simService;

    @GetMapping("/simula")
    public String simula(@RequestParam(defaultValue = "static") String mode) {
        try {
            return simService.run(mode);
        } catch (Exception e) {
            return "Errore durante la simulazione: " + e.getMessage();
        }
    }
}
