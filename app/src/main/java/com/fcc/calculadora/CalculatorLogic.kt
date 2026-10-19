package com.fcc.calculadora

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.*

// Lo que devuelve el motor: un número o un mensaje de error
sealed class Resultado {
    data class Ok(val valor: Double) : Resultado()
    data class Error(val mensaje: String) : Resultado()
}

class CalculadoraException(mensaje: String) : Exception(mensaje)

object CalculatorEngine {

    // De la más larga a la más corta, para que "sinh⁻¹" no se lea como "sin"
    private val funciones = listOf(
        "sinh⁻¹", "cosh⁻¹", "tanh⁻¹",
        "sin⁻¹", "cos⁻¹", "tan⁻¹",
        "sinh", "cosh", "tanh",
        "sin", "cos", "tan",
        "log₂", "log", "ln", "³√", "√"
    )

    fun evaluar(expresion: String, enGrados: Boolean): Resultado {
        if (expresion.isBlank()) return Resultado.Error("Expresión vacía")
        return try {
            val valor = Parser(expresion, enGrados).parsear()
            when {
                valor.isNaN() -> Resultado.Error("Error")
                valor.isInfinite() -> Resultado.Error("Desbordamiento")
                else -> Resultado.Ok(limpiar(valor))
            }
        } catch (e: CalculadoraException) {
            Resultado.Error(e.message ?: "Error")
        }
    }

    // Quita basura de precisión: sin(180°) da 0.00000000000000012 en vez de 0
    private fun limpiar(x: Double): Double {
        if (abs(x) < 1e-12) return 0.0
        return BigDecimal(x).round(MathContext(12)).toDouble()
    }

    // Convierte el número a texto para mostrarlo en pantalla
    fun formatear(x: Double): String {
        val bd = BigDecimal(x).round(MathContext(10)).stripTrailingZeros()
        val texto = if (abs(x) == 0.0 || (abs(x) >= 1e-6 && abs(x) < 1e15)) {
            bd.toPlainString()
        } else {
            bd.toString().replace("E+", "E")
        }
        return texto.replace('-', '−')
    }

    private class Parser(private val s: String, private val grados: Boolean) {
        private var pos = 0

        fun parsear(): Double {
            val v = expresion()
            if (pos < s.length) throw CalculadoraException("Expresión inválida")
            return v
        }

        // ---------- utilidades ----------
        private fun ver(): Char? = s.getOrNull(pos)
        private fun empiezaCon(t: String) = s.startsWith(t, pos)
        private fun consumir(t: String): Boolean {
            if (empiezaCon(t)) { pos += t.length; return true }
            return false
        }

        private fun empiezaPrimario(): Boolean {
            val c = ver() ?: return false
            return c.isDigit() || c == '.' || c == 'π' || c == 'e' || c == '(' ||
                    funciones.any { empiezaCon(it) }
        }

        // ---------- niveles de jerarquía ----------

        // Nivel 1: suma y resta
        private fun expresion(): Double {
            var v = termino()
            while (true) {
                v = when {
                    consumir("+") -> v + termino()
                    consumir("−") || consumir("-") -> v - termino()
                    else -> return v
                }
            }
        }

        // Nivel 2: multiplicación, división y multiplicación implícita (2π, 3(4))
        private fun termino(): Double {
            var v = unario()
            while (true) {
                v = when {
                    consumir("×") -> v * unario()
                    consumir("÷") -> {
                        val divisor = unario()
                        if (divisor == 0.0) throw CalculadoraException("No se puede dividir entre 0")
                        v / divisor
                    }
                    empiezaPrimario() -> v * unario()
                    else -> return v
                }
            }
        }

        // Nivel 3: signo negativo
        private fun unario(): Double =
            if (consumir("−") || consumir("-")) -unario() else potencia()

        // Nivel 4: potencias, raíz y-ésima y logaritmo en base y
        private fun potencia(): Double {
            val base = postfijo()
            return when {
                consumir("^") -> elevar(base, unario())
                consumir("ʸ√") -> raizY(base, unario())
                consumir("logᵧ") -> logBase(base, unario())
                else -> base
            }
        }

        // Nivel 5: operadores que van después del número
        private fun postfijo(): Double {
            var v = primario()
            while (true) {
                v = when {
                    consumir("²") -> v * v
                    !empiezaCon("³√") && consumir("³") -> v * v * v
                    consumir("⁻¹") -> {
                        if (v == 0.0) throw CalculadoraException("No se puede dividir entre 0")
                        1 / v
                    }
                    consumir("!") -> factorial(v)
                    consumir("%") -> v / 100
                    else -> return v
                }
            }
        }

        // Nivel 6: números, constantes, paréntesis y funciones
        private fun primario(): Double {
            val c = ver() ?: throw CalculadoraException("Expresión incompleta")
            if (c.isDigit() || c == '.') return numero()
            if (consumir("π")) return PI
            if (consumir("e")) return E
            if (consumir("(")) {
                val v = expresion()
                cerrar()
                return v
            }
            for (f in funciones) {
                if (consumir(f)) {
                    val arg = if (consumir("(")) {
                        val v = expresion()
                        cerrar()
                        v
                    } else {
                        unario()
                    }
                    return aplicar(f, arg)
                }
            }
            throw CalculadoraException("Expresión inválida")
        }

        // Si falta el ")" al final, se cierra solo
        private fun cerrar() {
            if (!consumir(")") && pos < s.length) {
                throw CalculadoraException("Expresión inválida")
            }
        }

        private fun numero(): Double {
            val inicio = pos
            while (ver()?.let { it.isDigit() || it == '.' } == true) pos++
            if (ver() == 'E') {   // notación EE: 5E3 = 5000
                pos++
                if (ver() == '−' || ver() == '-') pos++
                while (ver()?.isDigit() == true) pos++
            }
            val texto = s.substring(inicio, pos).replace('−', '-')
            return texto.toDoubleOrNull() ?: throw CalculadoraException("Número inválido")
        }

        // ---------- operaciones con validación ----------

        private fun aRad(x: Double) = if (grados) Math.toRadians(x) else x
        private fun deRad(x: Double) = if (grados) Math.toDegrees(x) else x

        private fun aplicar(f: String, x: Double): Double = when (f) {
            "sin" -> sin(aRad(x))
            "cos" -> cos(aRad(x))
            "tan" -> tangente(x)
            "sin⁻¹" -> {
                if (x < -1 || x > 1) throw CalculadoraException("Fuera de dominio")
                deRad(asin(x))
            }
            "cos⁻¹" -> {
                if (x < -1 || x > 1) throw CalculadoraException("Fuera de dominio")
                deRad(acos(x))
            }
            "tan⁻¹" -> deRad(atan(x))
            "sinh" -> sinh(x)
            "cosh" -> cosh(x)
            "tanh" -> tanh(x)
            "sinh⁻¹" -> asinh(x)
            "cosh⁻¹" -> {
                if (x < 1) throw CalculadoraException("Fuera de dominio")
                acosh(x)
            }
            "tanh⁻¹" -> {
                if (x <= -1 || x >= 1) throw CalculadoraException("Fuera de dominio")
                atanh(x)
            }
            "ln" -> {
                if (x <= 0) throw CalculadoraException("Logaritmo inválido")
                ln(x)
            }
            "log₂" -> {
                if (x <= 0) throw CalculadoraException("Logaritmo inválido")
                log2(x)
            }
            "log" -> {
                if (x <= 0) throw CalculadoraException("Logaritmo inválido")
                log10(x)
            }
            "√" -> {
                if (x < 0) throw CalculadoraException("Raíz de número negativo")
                sqrt(x)
            }
            "³√" -> cbrt(x)
            else -> throw CalculadoraException("Función desconocida")
        }

        private fun tangente(x: Double): Double {
            if (grados) {
                val resto = ((x % 180) + 180) % 180
                if (abs(resto - 90) < 1e-9) throw CalculadoraException("Indefinido")
            } else if (abs(cos(x)) < 1e-12) {
                throw CalculadoraException("Indefinido")
            }
            return tan(aRad(x))
        }

        private fun factorial(x: Double): Double {
            if (x < 0 || x != floor(x)) throw CalculadoraException("Factorial inválido")
            if (x > 170) throw CalculadoraException("Desbordamiento")
            var r = 1.0
            for (i in 2..x.toInt()) r *= i
            return r
        }

        private fun elevar(base: Double, exp: Double): Double {
            if (base == 0.0 && exp < 0) throw CalculadoraException("No se puede dividir entre 0")
            if (base < 0 && exp != floor(exp)) throw CalculadoraException("Potencia inválida")
            return base.pow(exp)
        }

        // x logᵧ y = logaritmo de x en base y
        private fun logBase(x: Double, y: Double): Double {
            if (x <= 0 || y <= 0 || y == 1.0) throw CalculadoraException("Logaritmo inválido")
            return ln(x) / ln(y)
        }

        // x ʸ√ y = raíz y-ésima de x
        private fun raizY(x: Double, y: Double): Double {
            if (y == 0.0) throw CalculadoraException("Raíz inválida")
            if (x < 0) {
                if (y != floor(y)) throw CalculadoraException("Raíz inválida")
                if (y.toLong() % 2 == 0L) throw CalculadoraException("Raíz de número negativo")
                return -(-x).pow(1 / y)   // raíz impar de negativo: ³√-8 = -2
            }
            return x.pow(1 / y)
        }
    }
}