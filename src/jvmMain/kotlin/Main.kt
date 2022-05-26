// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.animation.animateColorAsState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.window.AwtWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import javax.imageio.ImageIO

@OptIn(ExperimentalMaterialApi::class)
@Composable
@Preview
fun App(location: MutableState<Int>) {

    var writing by remember { mutableStateOf(false) }
    var filePicker by remember { mutableStateOf(false) }
    var writingDone by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    if (writing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Writing To File") },
            text = { CircularProgressIndicator() },
            buttons = {}
        )
    }

    if (writingDone) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Writing To File") },
            text = { Text("Writing Done!") },
            buttons = {}
        )
    }

    val pokemons = remember {
        Pokemon::class.java.getResourceAsStream("pokemons.json")?.readAllBytes()
            ?.toString(Charset.defaultCharset())
            .fromJson<Array<Pokemon>>().orEmpty()
    }

    //var location by remember { mutableStateOf(0) }

    val pokemon = pokemons.getOrNull(location.value)

    val alex = remember { Character("Alex") }
    val amun = remember { Character("Amun") }
    val andy = remember { Character("Andy") }
    val era = remember { Character("Era") }
    val ginko = remember { Character("Ginko") }

    if (filePicker) {
        FileDialog { file ->
            filePicker = false
            file?.let { readFile(File(it), alex, amun, andy, era, ginko) }
        }
    }

    Card(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = { filePicker = true }) { Text("Import CSV Data (Will Overwrite Current Data)") }
                Button(
                    onClick = {
                        writing = true
                        val f = """
,${alex.name},${amun.name},${andy.name},${era.name},${ginko.name}
${
                            pokemons.joinToString("\n") {
                                "${it.name},${alex.getChoice(it.id)},${amun.getChoice(it.id)},${andy.getChoice(it.id)},${era.getChoice(it.id)},${
                                    ginko.getChoice(
                                        it.id
                                    )
                                }"
                            }
                        }
                    """.trimIndent()
                        val userHomeFolder = System.getProperty("user.home")
                        val file = File("$userHomeFolder${File.separator}Desktop", "pokemons.csv")
                        if (!file.exists()) file.createNewFile()
                        file.writeText(f)
                        writing = false
                        writingDone = true
                        scope.launch {
                            delay(2000)
                            writingDone = false
                        }
                    }
                ) { Text("Export Data to CSV") }
            }
            pokemon?.let {
                PokemonContent(it, pokemons.size)
                Characters(it, alex, amun, andy, era, ginko)
            }

            Slider(
                value = location.value.toFloat(),
                onValueChange = { location.value = it.toInt() },
                valueRange = 0f..pokemons.size.toFloat()
            )

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = { location.value-- }) { Text("Previous") }
                Button(onClick = { location.value++ }) { Text("Next") }
            }
        }
    }
}

// read in csv file and populate characters with choices
fun readFile(file: File, vararg character: Character) {
    val lines = file.readLines()
    val chars = lines[0].split(",").drop(1)
    val choices = lines.drop(1).map { it.split(",").drop(1) }
    choices.forEachIndexed { index, strings ->
        chars.forEachIndexed { indexC, _ ->
            character[indexC].also { c ->
                c.choices[(index + 1).toString().padStart(3, '0')] = Choice.valueOf(strings[indexC])
            }
        }
    }
}

@Composable
fun Characters(pokemon: Pokemon, vararg characters: Character) {
    characters.forEach { Character(it, pokemon) }
}

@Composable
fun Character(character: Character, pokemon: Pokemon) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(character.name)

        val choice = character.choices.getOrDefault(pokemon.id, Choice.Undecided)

        Row {
            OutlinedButton(
                onClick = { character.choices[pokemon.id] = Choice.Smash },
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = animateColorAsState(if (choice == Choice.Smash) Emerald else MaterialTheme.colors.surface).value
                )
            ) { Text("Smash") }

            OutlinedButton(
                onClick = { character.choices[pokemon.id] = Choice.Pass },
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = animateColorAsState(if (choice == Choice.Pass) Alizarin else MaterialTheme.colors.surface).value,
                )
            ) { Text("Pass") }
        }
    }
}

val Emerald = Color(0xFF2ecc71)
val Sunflower = Color(0xFFf1c40f)
val Alizarin = Color(0xFFe74c3c)

@Composable
fun PokemonContent(pokemon: Pokemon, size: Int) {
    Text(pokemon.id + "/$size")
    Text(pokemon.name.capitalize(Locale.current))
    Image(bitmap = loadNetworkImage(pokemon.image_hq), null)
}

class Character(val name: String) {

    var choices = mutableStateMapOf<String, Choice>()

    fun getChoice(id: String) = choices.getOrDefault(id, Choice.Undecided)
}

enum class Choice { Undecided, Smash, Pass }

inline fun <reified T> String?.fromJson(): T? = try {
    Gson().fromJson(this, object : TypeToken<T>() {}.type)
} catch (e: Exception) {
    e.printStackTrace()
    null
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    MaterialTheme(darkColors()) {
        val location = remember { mutableStateOf(0) }
        Window(
            title = "Pokemon Smash Or Pass",
            onCloseRequest = ::exitApplication,
            onPreviewKeyEvent = {
                if (it.type == KeyEventType.KeyDown) {
                    when (it.key) {
                        Key.DirectionLeft -> {
                            location.value--
                            true
                        }
                        Key.DirectionRight -> {
                            location.value++
                            true
                        }
                        else -> false
                    }
                } else false
            }
        ) { App(location) }
    }
}

data class Pokemon(val id: String, val name: String, val image_hq: String, val image: String, val types: Any?)

fun loadNetworkImage(link: String): ImageBitmap {
    val url = URL(link)
    val connection = url.openConnection() as HttpURLConnection
    connection.connect()

    val inputStream = connection.inputStream
    val bufferedImage = ImageIO.read(inputStream)

    val stream = ByteArrayOutputStream()
    ImageIO.write(bufferedImage, "png", stream)
    val byteArray = stream.toByteArray()

    return org.jetbrains.skia.Image.makeFromEncoded(byteArray).toComposeImageBitmap()
}

@Composable
private fun FileDialog(
    parent: Frame? = null,
    onCloseRequest: (result: String?) -> Unit
) = AwtWindow(
    create = {
        object : FileDialog(parent, "Choose a file", LOAD) {
            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    onCloseRequest(directory + File.separator + file)
                }
            }
        }.apply {
            setFilenameFilter { _, name -> name.endsWith(".csv") }
        }
    },
    dispose = FileDialog::dispose
)