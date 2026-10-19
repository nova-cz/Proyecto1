package com.fcc.calculadora

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.fcc.calculadora.databinding.ActivityMainBinding
import com.fcc.calculadora.databinding.ItemHistorialBinding
import com.fcc.calculadora.databinding.SheetHistorialBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Sobrevive a la rotación: al recrearse la Activity, recibe el mismo ViewModel
    private val viewModel: CalculatorViewModel by viewModels()

    // La hoja del historial, para poder cerrarla si se destruye la Activity
    private var dialogoHistorial: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Deja espacio para la barra de estado, la de navegación y la cámara
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        conectarTeclas()

        // Cada vez que cambia el estado, se vuelve a dibujar la pantalla
        viewModel.estado.observe(this) { estado -> dibujar(estado) }
    }

    private fun conectarTeclas() {
        val b = binding
        val vm = viewModel

        // ----- Teclas que existen en las dos orientaciones -----
        listOf(b.btn0, b.btn1, b.btn2, b.btn3, b.btn4, b.btn5, b.btn6, b.btn7, b.btn8, b.btn9)
            .forEachIndexed { numero, tecla ->
                tecla.setOnClickListener { vm.digito(numero.toString()) }
            }

        b.btnPunto.setOnClickListener { vm.punto() }
        b.btnSumar.setOnClickListener { vm.operador("+") }
        b.btnRestar.setOnClickListener { vm.operador("−") }
        b.btnMultiplicar.setOnClickListener { vm.operador("×") }
        b.btnDividir.setOnClickListener { vm.operador("÷") }
        b.btnIgual.setOnClickListener { vm.igual() }
        b.btnAC.setOnClickListener { vm.limpiar() }
        b.btnBorrar.setOnClickListener { vm.borrar() }
        b.btnSigno.setOnClickListener { vm.cambiarSigno() }
        b.btnPorcentaje.setOnClickListener { vm.postfijo("%") }
        b.btnHistorial.setOnClickListener { abrirHistorial() }

        // ----- Solo en la científica (?. porque en portrait no existen) -----
        b.btnParAbre?.setOnClickListener { vm.insertar("(") }
        b.btnParCierra?.setOnClickListener { vm.cerrarParentesis() }

        b.btnMC?.setOnClickListener { vm.memoriaLimpiar() }
        b.btnMMas?.setOnClickListener { vm.memoriaSumar() }
        b.btnMMenos?.setOnClickListener { vm.memoriaRestar() }
        b.btnMR?.setOnClickListener { vm.memoriaRecuperar() }

        b.btn2nd?.setOnClickListener { vm.alternarSegunda() }
        b.btnRad?.setOnClickListener { vm.alternarGrados() }

        b.btnCuadrado?.setOnClickListener { vm.postfijo("²") }
        b.btnCubo?.setOnClickListener { vm.postfijo("³") }
        b.btnPotencia?.setOnClickListener { vm.operador("^") }
        b.btnExp?.setOnClickListener { vm.teclaExp() }
        b.btn10x?.setOnClickListener { vm.tecla10x() }

        b.btnInverso?.setOnClickListener { vm.postfijo("⁻¹") }
        b.btnRaiz2?.setOnClickListener { vm.funcion("√") }
        b.btnRaiz3?.setOnClickListener { vm.funcion("³√") }
        b.btnRaizY?.setOnClickListener { vm.operador("ʸ√") }
        b.btnLn?.setOnClickListener { vm.teclaLn() }
        b.btnLog?.setOnClickListener { vm.teclaLog() }

        b.btnFactorial?.setOnClickListener { vm.postfijo("!") }
        b.btnSin?.setOnClickListener { vm.funcion("sin") }
        b.btnCos?.setOnClickListener { vm.funcion("cos") }
        b.btnTan?.setOnClickListener { vm.funcion("tan") }
        b.btnE?.setOnClickListener { vm.insertar("e") }
        b.btnEE?.setOnClickListener { vm.ee() }

        b.btnRand?.setOnClickListener { vm.aleatorio() }
        b.btnSinh?.setOnClickListener { vm.funcion("sinh") }
        b.btnCosh?.setOnClickListener { vm.funcion("cosh") }
        b.btnTanh?.setOnClickListener { vm.funcion("tanh") }
        b.btnPi?.setOnClickListener { vm.insertar("π") }
    }

    private fun dibujar(e: EstadoUi) {
        val b = binding

        // Display
        b.tvResultado.text = e.display
        b.tvResultado.setTextColor(color(if (e.esError) R.color.error else R.color.texto))
        b.tvExpresion.text = e.expresionArriba

        // Deg / Rad: el indicador muestra el modo actual y la tecla el otro
        b.tvModo?.text = if (e.grados) "Deg" else "Rad"
        b.btnRad?.text = if (e.grados) "Rad" else "Deg"

        // 2nd: cambia las etiquetas a sus inversas y se resalta en azul
        val inv = if (e.segunda) "⁻¹" else ""
        b.btnSin?.text = "sin$inv"
        b.btnCos?.text = "cos$inv"
        b.btnTan?.text = "tan$inv"
        b.btnSinh?.text = "sinh$inv"
        b.btnCosh?.text = "cosh$inv"
        b.btnTanh?.text = "tanh$inv"
        b.btnExp?.text = if (e.segunda) "yˣ" else "eˣ"
        b.btn10x?.text = if (e.segunda) "2ˣ" else "10ˣ"
        b.btnLn?.text = if (e.segunda) "logᵧ" else "ln"
        b.btnLog?.text = if (e.segunda) "log₂" else "log₁₀"
        b.btn2nd?.setTextColor(color(if (e.segunda) R.color.tecla_op_texto else R.color.tecla_fn_texto))

        // mr en azul cuando hay algo guardado en memoria
        b.btnMR?.setTextColor(color(if (e.hayMemoria) R.color.tecla_op_texto else R.color.tecla_fn_texto))
    }

    private fun abrirHistorial() {
        val dialogo = BottomSheetDialog(this)
        val hoja = SheetHistorialBinding.inflate(layoutInflater)
        dialogo.setContentView(hoja.root)

        // Quita el fondo blanco que pone Material para que se vea nuestro diseño glass
        dialogo.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            background = null
            backgroundTintList = null
        }
        dialogo.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialogo.behavior.skipCollapsed = true

        val lista = viewModel.historial.value.orEmpty()
        hoja.tvVacio.isVisible = lista.isEmpty()
        hoja.btnBorrarHistorial.isVisible = lista.isNotEmpty()

        // Una tarjeta por operación; al tocarla se carga en el display
        lista.forEachIndexed { indice, entrada ->
            val item = ItemHistorialBinding.inflate(layoutInflater, hoja.listaHistorial, false)
            item.tvItemExpresion.text = entrada.expresion
            item.tvItemResultado.text = "= ${entrada.resultado}"
            item.root.setOnClickListener {
                viewModel.usarHistorial(indice)
                dialogo.dismiss()
            }
            hoja.listaHistorial.addView(item.root)
        }

        hoja.btnListo.setOnClickListener { dialogo.dismiss() }
        hoja.btnBorrarHistorial.setOnClickListener {
            viewModel.borrarHistorial()
            dialogo.dismiss()
        }

        dialogoHistorial = dialogo
        dialogo.show()
    }

    // Si giras con la hoja abierta, se cierra limpio en vez de dejar una ventana colgada
    override fun onDestroy() {
        dialogoHistorial?.dismiss()
        super.onDestroy()
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
}