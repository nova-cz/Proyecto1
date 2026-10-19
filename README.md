# Calculadora

Calculadora científica en Android (Kotlin). Básica en vertical y científica en horizontal, con historial, validación de operaciones y funciones trigonométricas en grados.

Proyecto 1 de Desarrollo de Aplicaciones Móviles, BUAP.

## Arquitectura

El proyecto sigue el patrón **MVVM**:

- `MainActivity.kt`: vista (UI); muestra la pantalla y envía los eventos de los botones al ViewModel.
- `CalculatorViewModel.kt`: mantiene el estado (expresión, resultado, historial) y coordina la lógica.
- `CalculatorLogic.kt`: lógica de cálculo, validación de operaciones y funciones trigonométricas en grados.

Los archivos están en `app/src/main/java/com/fcc/calculadora/`.
