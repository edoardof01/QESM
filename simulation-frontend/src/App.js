import { useState } from "react";
import './index.css';

export default function SimulationViewer() {

    const [mode, setMode] = useState("static");
    const [rounds, setRounds] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState("");
    const [inputValue, setInputValue] = useState(1);

    async function runSimulation() {
        setLoading(true);
        setError("");
        setRounds([]);

        const finalRoundCount = Math.min(20, Math.max(1, Number(inputValue) || 1));
        // Rimuovi la riga qui sotto:
        // setRoundCount(finalRoundCount);

        try {
            const start = await fetch(
                `http://localhost:8080/simulate?mode=${mode}&rounds=${finalRoundCount}`,
                { method: "GET" }
            );
            if (!start.ok) throw new Error("Impossibile avviare la simulazione");

            await new Promise(resolve => setTimeout(resolve, 1000));

            const loaded = [];
            for (let i = 1; i <= finalRoundCount; i++) {
                let attempts = 0;
                const maxAttempts = 5;

                while (attempts < maxAttempts) {
                    try {
                        const res = await fetch(`/output/round_${i}_results.json`);
                        if (res.ok) {
                            const data = await res.json();
                            loaded.push(data);
                            break;
                        } else if (attempts === maxAttempts - 1) {
                            throw new Error(`File round_${i}_results.json non trovato dopo ${maxAttempts} tentativi`);
                        }
                    } catch (err) {
                        if (attempts === maxAttempts - 1) {
                            throw new Error(`Errore caricando round_${i}_results.json: ${err.message}`);
                        }
                    }

                    attempts++;
                    await new Promise(resolve => setTimeout(resolve, 500));
                }
            }
            setRounds(loaded);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    const handleInputChange = (e) => {
        const value = e.target.value;
        setInputValue(value);

        const numValue = Number(value);
        if (!isNaN(numValue) && numValue >= 1 && numValue <= 20) {
            // Rimuovi la riga qui sotto:
            // setRoundCount(numValue);
        }
    };

    const handleInputBlur = () => {
        let value = Number(inputValue);
        if (isNaN(value)) value = 1;
        value = Math.min(20, Math.max(1, value));
        // Rimuovi le due righe qui sotto:
        // setRoundCount(value);
        setInputValue(value);
    };

    return (
        <div className="mx-10 max-w-7xl p-6 space-y-4">
            <h1 className="text-2xl m-3 font-bold">Dashboard simulazione BPH₄</h1>

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
                    className="px-4 py-2 bg-blue-600 text-white rounded shadow disabled:bg-gray-400"
                >
                    {loading ? "Simulazione..." : "Avvia simulazione"}
                </button>
            </div>

            {error && (
                <div className="p-4 bg-red-100 border border-red-400 text-red-700 rounded">
                    {error}
                </div>
            )}

            {loading && (
                <div className="p-4 bg-blue-100 border border-blue-400 text-blue-700 rounded">
                    Eseguendo simulazione con {Math.min(20, Math.max(1, Number(inputValue) || 1))} round in modalità {mode}...
                </div>
            )}

            {rounds.length > 0 && (
                <div className="mt-6">
                    <h2 className="text-xl font-semibold mb-4">
                        Risultati simulazione ({rounds.length} round{rounds.length > 1 ? 's' : ''})
                    </h2>

                    {rounds.map(r => (
                        <details key={r.round} className="border p-4 rounded mb-4 bg-gray-50">
                            <summary className="cursor-pointer font-semibold text-lg hover:text-blue-600">
                                Round {r.round} – {r.mode}
                            </summary>

                            <div className="mt-4 grid grid-cols-1 md:grid-cols-3 gap-4">
                                <div className="bg-white p-3 rounded shadow">
                                    <h4 className="font-semibold text-red-600">Abbandono</h4>
                                    <p className="text-2xl">{r.abbandono.toFixed(4)}</p>
                                </div>
                                <div className="bg-white p-3 rounded shadow">
                                    <h4 className="font-semibold text-orange-600">Blocco</h4>
                                    <p className="text-2xl">{r.blocco.toFixed(4)}</p>
                                </div>
                                <div className="bg-white p-3 rounded shadow">
                                    <h4 className="font-semibold text-green-600">Utilizzo</h4>
                                    <p className="text-2xl">{r.utilizzo.toFixed(4)}</p>
                                </div>
                            </div>

                            <div className="mt-4 bg-white p-3 rounded shadow">
                                <h4 className="font-semibold">Pesi</h4>
                                <div className="flex flex-wrap gap-2 mt-2">
                                    {r.weights.map((w, i) => (
                                        <span key={i} className="px-2 py-1 bg-blue-100 text-blue-800 rounded text-sm">
                                            W{i + 1} = {w}
                                        </span>
                                    ))}
                                </div>
                            </div>

                            <h4 className="mt-4 font-semibold">Grafici</h4>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-4">
                                <div className="bg-white p-2 rounded shadow">
                                    <h5 className="text-sm font-medium mb-2">CDF Empirica</h5>
                                    <img
                                        src={`/output/${r.images.cdf}`}
                                        alt="CDF"
                                        className="w-full h-auto max-h-96 object-contain border"
                                        onError={(e) => {
                                            e.target.style.display = 'none';
                                            e.target.nextSibling.style.display = 'block';
                                        }}
                                    />
                                    <div style={{display: 'none'}} className="text-red-500 text-sm">
                                        Immagine non disponibile
                                    </div>
                                </div>

                                <div className="bg-white p-2 rounded shadow">
                                    <h5 className="text-sm font-medium mb-2">Istogramma Inter-arrivi</h5>
                                    <img
                                        src={`/output/${r.images.hist}`}
                                        alt="Istogramma inter-arrivi"
                                        className="w-full h-auto max-h-96 object-contain border"
                                        onError={(e) => {
                                            e.target.style.display = 'none';
                                            e.target.nextSibling.style.display = 'block';
                                        }}
                                    />
                                    <div style={{display: 'none'}} className="text-red-500 text-sm">
                                        Immagine non disponibile
                                    </div>
                                </div>

                                <div className="bg-white p-2 rounded shadow">
                                    <h5 className="text-sm font-medium mb-2">BPH Fit</h5>
                                    <img
                                        src={`/output/${r.images.fit}`}
                                        alt="BPH Fit"
                                        className="w-full h-auto max-h-96 object-contain border"
                                        onError={(e) => {
                                            e.target.style.display = 'none';
                                            e.target.nextSibling.style.display = 'block';
                                        }}
                                    />
                                    <div style={{display: 'none'}} className="text-red-500 text-sm">
                                        Immagine non disponibile
                                    </div>
                                </div>
                            </div>
                        </details>
                    ))}
                </div>
            )}
        </div>

    );
}