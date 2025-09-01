package myPackage.endpoint;

import org.springframework.core.io.Resource;
import myPackage.service.SimulazioneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class SimulazioneEndpoint {

    @Autowired
    private SimulazioneService simService;

    @GetMapping("/simulate")
    public String simula(@RequestParam(defaultValue = "static") String mode, @RequestParam int rounds ) {
        try {
            return simService.run(mode, rounds);
        } catch (Exception e) {
            return "Errore durante la simulazione: " + e.getMessage();
        }
    }

    @GetMapping("/output/{filename:.+}")
    public ResponseEntity<Resource> serveOutput(@PathVariable String filename) throws IOException {
        // Usa System.getProperty("user.dir") per ottenere la directory di lavoro corrente
        // e Paths.get per costruire un percorso compatibile con il sistema operativo
        File file = Paths.get(System.getProperty("user.dir"), "output", filename).toFile();

        System.out.println("🔍 Cerco file: " + file.getAbsolutePath());

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = filename.endsWith(".json") ? "application/json" : "image/png";
        Resource resource = new InputStreamResource(new FileInputStream(file));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }
}