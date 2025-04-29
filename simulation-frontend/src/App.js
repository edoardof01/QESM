import { useState } from "react";
import './index.css';

export default function SimulationViewer() {

    const [mode, setMode] = useState("static");
    const [roundCount, setRoundCount] = useState(1);
    const [rounds, setRounds] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [inputValue, setInputValue] = useState(1); // valore raw dell'input

    async function runSimulation() {
        setLoading(true);
        setError("");
        setRounds([]);

        try {
            const start = await fetch(
                `http://localhost:8081/simulate?mode=${mode}&rounds=${roundCount}`,
                { method: "GET" }
            );
            if (!start.ok) throw new Error("Impossibile avviare la simulazione");

            // 2. Carica tutti i JSON generati
            const loaded = [];
            for (let i = 1; i <= roundCount; i++) {
                const res = await fetch(`/output/round_${i}_results.json`);
                if (!res.ok) throw new Error(`Manca round_${i}_results.json`);
                loaded.push(await res.json());
            }
            setRounds(loaded);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    const handleInputChange = (e) => {
        setInputValue(e.target.value);
    };

    const handleInputBlur = () => {
        let value = Number(inputValue);
        if (isNaN(value)) value = 1;
        value = Math.min(20, Math.max(1, value)); // forziamo tra 1 e 20
        setRoundCount(value);
        setInputValue(value); // aggiorna il valore nell'input
    };

    return (
        <div className="mx-10 max-w-7xl p-6 space-y-4">
            <h1 className="text-2xl m-3 font-bold">Dashboard simulazione BPH₄</h1>

            {/* Controlli utente */}
            <div className="flex items-center gap-4">
                <select
                    value={mode}
                    onChange={e => setMode(e.target.value)}
                    className="border p-1"
                >
                    <option value="static">Static</option>
                    <option value="dynamic">Dynamic</option>
                </select>

                <input
                    type="number"
                    min="1"
                    max="20"
                    value={inputValue}
                    onChange={handleInputChange}
                    onBlur={handleInputBlur}
                    className="border p-1 w-24"
                />

                <button
                    onClick={runSimulation}
                    disabled={loading}
                    className="px-4 py-2 bg-blue-600 text-white rounded shadow"
                >
                    {loading ? "Simulazione..." : "Avvia simulazione"}
                </button>
            </div>

            {/* Messaggio di errore */}
            {error && <p className="text-red-600">{error}</p>}

            {/* Visualizzazione dei risultati */}
            {rounds.map(r => (
                <details key={r.round} className="border p-4 rounded">
                    <summary className="cursor-pointer font-semibold">
                        Round {r.round} – {r.mode}
                    </summary>

                    <p>Abbandono: {r.abbandono.toFixed(4)}</p>
                    <p>Blocco: {r.blocco.toFixed(4)}</p>
                    <p>Utilizzo: {r.utilizzo.toFixed(4)}</p>

                    <h4 className="mt-2 font-semibold">Pesi</h4>
                    <ul className="list-disc list-inside">
                        {r.weights.map((w, i) => (
                            <li key={i}>W{i + 1} = {w}</li>
                        ))}
                    </ul>

                    <h4 className="mt-3 font-semibold">Grafici</h4>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-4">
                        <img
                            src={`/output/${r.images.cdf}`}
                            alt="CDF"
                            className="w-full h-auto max-h-96 object-contain border"
                        />
                        <img
                            src={`/output/${r.images.hist}`}
                            alt="Istogramma inter-arrivi"
                            className="w-full h-auto max-h-96 object-contain border"
                        />
                        <img
                            src={`/output/${r.images.fit}`}
                            alt="BPH Fit"
                            className="w-full h-auto max-h-96 object-contain border"
                        />
                    </div>
                </details>
            ))}
        </div>
    );
}
