package com.fcc.calculadora

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlin.random.Random

// Una operación del historial
data class EntradaHistorial(val expresion: String, val resultado: String)

// Todo lo que la pantalla necesita para dibujarse
data class EstadoUi(
    val display: String,
    val expresionArriba: String,
    val esError: Boolean,
    val segunda: Boolean,
    val grados: Boolean,
    val hayMemoria: Boolean
)

class CalculatorViewModel(private val guardado: SavedStateHandle) : ViewModel() {

    // ---------- Estado (respaldado en SavedStateHandle para sobrevivir al giro) ----------

    private var entrada: String
        get() = guardado["entrada"] ?: "0"
        set(v) { guardado["entrada"] = v }

    private var arriba: String
        get() = guardado["arriba"] ?: ""
        set(v) { guardado["arriba"] = v }

    private var esError: Boolean
        get() = guardado["esError"] ?: false
        set(v) { guardado["esError"] = v }

    private var justoEvaluo: Boolean
        get() = guardado["justoEvaluo"] ?: false
        set(v) { guardado["justoEvaluo"] = v }

    private var segunda: Boolean
        get() = guardado["segunda"] ?: false
        set(v) { guardado["segunda"] = v }

    private var grados: Boolean
        get() = guardado["grados"] ?: true      // grados por defecto, como pide el profe
        set(v) { guardado["grados"] = v }

    private var memoria: Double
        get() = guardado["memoria"] ?: 0.0
        set(v) { guardado["memoria"] = v }

    // Historial: lo más reciente al inicio, máximo 50
    private val historialInterno = ArrayDeque<EntradaHistorial>()

    // ---------- Lo que observa MainActivity ----------

    private val _estado = MutableLiveData<EstadoUi>()
    val estado: LiveData<EstadoUi> = _estado

    private val _historial = MutableLiveData<List<EntradaHistorial>>()
    val historial: LiveData<List<EntradaHistorial>> = _historial

    init {
        // Recupera el historial si Android había destruido el proceso
        val expresiones = guardado.get<ArrayList<String>>("histExp") ?: arrayListOf()
        val resultados = guardado.get<ArrayList<String>>("histRes") ?: arrayListOf()
        for (i in expresiones.indices) {
            historialInterno.addLast(EntradaHistorial(expresiones[i], resultados[i]))
        }
        publicar()
    }

    private fun publicar() {
        _estado.value = EstadoUi(entrada, arriba, esError, segunda, grados, memoria != 0.0)
        _historial.value = historialInterno.toList()
    }

    private fun guardarHistorial() {
        guardado["histExp"] = ArrayList(historialInterno.map { it.expresion })
        guardado["histRes"] = ArrayList(historialInterno.map { it.resultado })
    }

    // ---------- Ayudantes ----------

    private val operadores = listOf("logᵧ", "ʸ√", "+", "−", "×", "÷", "^")
    private val invertibles = setOf("sin", "cos", "tan", "sinh", "cosh", "tanh")

    // Tokens de varios caracteres que se borran completos con la tecla borrar
    private val tokensLargos = listOf(
        "sinh⁻¹(", "cosh⁻¹(", "tanh⁻¹(", "sin⁻¹(", "cos⁻¹(", "tan⁻¹(",
        "sinh(", "cosh(", "tanh(", "sin(", "cos(", "tan(",
        "log₂(", "log(", "ln(", "³√(", "√(", "logᵧ", "ʸ√", "⁻¹"
    )

    // El número que estás escribiendo al final, por ejemplo "3.5" en "2+3.5"
    private fun numeroActual(): String =
        entrada.takeLastWhile { it.isDigit() || it == '.' }

    private fun terminaEnOperador() = operadores.any { entrada.endsWith(it) }

    // Antes de cada tecla: si había error, se limpia. Si acabas de presionar =
    // y la tecla empieza un número nuevo, se borra el resultado anterior.
    private fun preparar(empiezaNuevo: Boolean) {
        if (esError) {
            entrada = "0"; arriba = ""; esError = false; justoEvaluo = false
            return
        }
        if (justoEvaluo) {
            if (empiezaNuevo) entrada = "0"
            arriba = ""
            justoEvaluo = false
        }
    }

    // ---------- Teclas ----------

    fun digito(d: String) {
        preparar(empiezaNuevo = true)
        if (numeroActual() == "0") entrada = entrada.dropLast(1)   // evita "07"
        entrada += d
        publicar()
    }

    fun punto() {
        preparar(empiezaNuevo = true)
        val num = numeroActual()
        when {
            num.contains('.') -> {}                 // segundo punto: se ignora
            num.isEmpty() -> entrada += "0."        // "5+" → "5+0."
            else -> entrada += "."
        }
        publicar()
    }

    fun operador(op: String) {
        preparar(empiezaNuevo = false)
        // Permite negativos después de × ÷ ^ ( como "5×−3"
        if (op == "−" && listOf("×", "÷", "^", "(", "ʸ√", "logᵧ").any { entrada.endsWith(it) }) {
            entrada += op
            publicar()
            return
        }
        // Si ya había operador al final, se reemplaza
        var e = entrada
        while (true) {
            val ultimo = operadores.firstOrNull { e.endsWith(it) } ?: break
            e = e.dropLast(ultimo.length)
        }
        if (e.isNotEmpty() && !e.endsWith("(") && !e.endsWith("E")) entrada = e + op
        publicar()
    }

    // Inserta algo que empieza un valor: constante, función, "(" o número
    fun insertar(texto: String) {
        preparar(empiezaNuevo = true)
        entrada = if (entrada == "0") texto else entrada + texto
        publicar()
    }

    fun funcion(base: String) {
        val nombre = if (segunda && base in invertibles) "${base}⁻¹" else base
        // Si en pantalla hay un resultado o un solo número, la función lo envuelve sin calcular
        val esSoloNumero = entrada != "0" && entrada.replace('−', '-').toDoubleOrNull() != null
        if (!esError && (justoEvaluo || esSoloNumero)) {
            entrada = "$nombre($entrada)"   // 1 → cos(1)
            arriba = ""
            justoEvaluo = false
            publicar()                       // solo lo muestra, el resultado sale al dar =
            return
        }
        insertar("$nombre(")
    }

    // ---------- Teclas que cambian con 2nd ----------

    // eˣ  →  con 2nd: yˣ (un número elevado a otro)
    fun teclaExp() {
        if (segunda) operador("^") else insertar("e^(")
    }

    // 10ˣ  →  con 2nd: 2ˣ
    fun tecla10x() {
        insertar(if (segunda) "2^(" else "10^(")
    }

    // ln  →  con 2nd: logᵧ (logaritmo de x en base y)
    fun teclaLn() {
        if (segunda) operador("logᵧ") else funcion("ln")
    }

    // log₁₀  →  con 2nd: log₂
    fun teclaLog() {
        funcion(if (segunda) "log₂" else "log")
    }

    fun cerrarParentesis() {
        preparar(empiezaNuevo = false)
        val abiertos = entrada.count { it == '(' } - entrada.count { it == ')' }
        if (abiertos > 0 && !entrada.endsWith("(") && !terminaEnOperador()) entrada += ")"
        publicar()
    }

    // ², ³, !, %, ⁻¹: solo después de un número, ")" o constante
    fun postfijo(s: String) {
        preparar(empiezaNuevo = false)
        val u = entrada.lastOrNull()
        val valido = u != null &&
                (u.isDigit() || u == ')' || u == 'π' || u == 'e' || u in "²³!%¹")
        if (valido) entrada += s
        publicar()
    }

    fun ee() {
        preparar(empiezaNuevo = false)
        if (entrada.lastOrNull()?.isDigit() == true) entrada += "E"
        publicar()
    }

    fun aleatorio() = insertar(CalculatorEngine.formatear(Random.nextDouble()))

    fun cambiarSigno() {
        preparar(empiezaNuevo = false)
        val num = numeroActual()
        if (num.isNotEmpty() && entrada != "0") {
            val antes = entrada.dropLast(num.length)
            val signoUnario = antes.endsWith("−") &&
                    (antes.length == 1 || antes.dropLast(1).last() in "+−×÷^(")
            entrada = if (signoUnario) antes.dropLast(1) + num else "$antes−$num"
        }
        publicar()
    }

    fun borrar() {
        if (esError || justoEvaluo) { limpiar(); return }
        val token = tokensLargos.firstOrNull { entrada.endsWith(it) }
        entrada = if (token != null) entrada.dropLast(token.length) else entrada.dropLast(1)
        if (entrada.isEmpty()) entrada = "0"
        publicar()
    }

    fun limpiar() {
        entrada = "0"; arriba = ""; esError = false; justoEvaluo = false
        publicar()
    }

    fun igual() {
        if (esError || justoEvaluo) return
        // Cierra los paréntesis que quedaron abiertos para que se vean bien
        val abiertos = entrada.count { it == '(' } - entrada.count { it == ')' }
        val expresion = entrada + ")".repeat(maxOf(0, abiertos))
        when (val r = CalculatorEngine.evaluar(expresion, grados)) {
            is Resultado.Ok -> {
                val texto = CalculatorEngine.formatear(r.valor)
                if (expresion != texto) {   // no guarda "5 = 5"
                    historialInterno.addFirst(EntradaHistorial(expresion, texto))
                    if (historialInterno.size > 50) historialInterno.removeLast()
                    guardarHistorial()
                }
                arriba = "$expresion ="
                entrada = texto
                justoEvaluo = true
            }
            is Resultado.Error -> {
                arriba = expresion
                entrada = r.mensaje
                esError = true
            }
        }
        publicar()
    }

    // ---------- Modos ----------

    fun alternarSegunda() { segunda = !segunda; publicar() }
    fun alternarGrados() { grados = !grados; publicar() }

    // ---------- Memoria ----------

    private fun valorActual(): Double? =
        (CalculatorEngine.evaluar(entrada, grados) as? Resultado.Ok)?.valor

    fun memoriaSumar() {
        if (esError) return
        valorActual()?.let { memoria += it; justoEvaluo = true }
        publicar()
    }

    fun memoriaRestar() {
        if (esError) return
        valorActual()?.let { memoria -= it; justoEvaluo = true }
        publicar()
    }

    fun memoriaLimpiar() { memoria = 0.0; publicar() }

    fun memoriaRecuperar() {
        val texto = CalculatorEngine.formatear(memoria)
        // Un negativo va entre paréntesis para que "3" + "−5" no se vuelva resta
        insertar(if (texto.startsWith("−") && entrada != "0" && !justoEvaluo) "($texto)" else texto)
    }

    // ---------- Historial ----------

    fun usarHistorial(indice: Int) {
        val e = historialInterno.elementAtOrNull(indice) ?: return
        entrada = e.expresion; arriba = ""; esError = false; justoEvaluo = false
        publicar()
    }

    fun borrarHistorial() {
        historialInterno.clear()
        guardarHistorial()
        publicar()
    }
}