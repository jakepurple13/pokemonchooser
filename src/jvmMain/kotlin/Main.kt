// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.AwtWindow
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import javax.imageio.ImageIO

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
@Preview
fun FrameWindowScope.App(location: MutableState<Int>) {

    var writing by remember { mutableStateOf(false) }
    var filePicker by remember { mutableStateOf(false) }
    var writingDone by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }

    var fileName by remember { mutableStateOf("") }

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
            text = {
                Column {
                    Text("Writing Done! Saved pokemons.csv to Desktop!")
                    Text(fileName)
                }
            },
            confirmButton = { TextButton(onClick = { writingDone = false }) { Text("OK") } }
        )
    }

    val pokemons = remember {
        Pokemon::class.java.getResourceAsStream("pokemons.json")?.readAllBytes()
            ?.toString(Charset.defaultCharset())
            .fromJson<Array<Pokemon>>().orEmpty()
    }

    val pokemon = pokemons.getOrNull(location.value)

    val characters = remember { mutableStateListOf<Character>() }

    if (filePicker) {
        FileDialog(
            FileDialogMode.Load,
            title = "Choose a file",
            block = { setFilenameFilter { _, name -> name.endsWith(".csv") } }
        ) { file ->
            filePicker = false
            file?.let { readFile(File(it), characters) }
        }
    }

    if (saveDialog) {
        FileDialog(
            FileDialogMode.Save,
            block = {
                setFilenameFilter { _, name -> name.endsWith(".csv") }
                file = "pokemons.csv"
            }
        ) { file ->
            val newFile = file?.let { f -> if (f.endsWith(".csv")) f else "$f.csv" }
            saveDialog = false
            writing = true
            newFile?.let { writeToFile(File(it), pokemons, *characters.toTypedArray()) }
            fileName = newFile.orEmpty()
            println(newFile)
            writing = false
            writingDone = true
        }
    }

    val scope = rememberCoroutineScope()
    val state = rememberScaffoldState()

    var dragState by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        window.dropTarget = DropTarget().apply {
            addDropTargetListener(object : DropTargetAdapter() {
                override fun dragEnter(dtde: DropTargetDragEvent?) {
                    super.dragEnter(dtde)
                    dragState = true
                }

                override fun drop(event: DropTargetDropEvent) {
                    event.acceptDrop(DnDConstants.ACTION_COPY)
                    val draggedFileName = event.transferable.getTransferData(DataFlavor.javaFileListFlavor)
                    println(draggedFileName)
                    when (draggedFileName) {
                        is List<*> -> {
                            draggedFileName.firstOrNull()?.toString()?.let {
                                if (it.endsWith(".csv")) {
                                    readFile(File(it), characters)
                                }
                            }
                        }
                    }
                    event.dropComplete(true)
                    dragState = false
                }

                override fun dragExit(dte: DropTargetEvent?) {
                    super.dragExit(dte)
                    dragState = false
                }
            })
        }
    }

    Scaffold(
        scaffoldState = state,
        drawerContent = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Stats") },
                        actions = { IconButton(onClick = { scope.launch { state.drawerState.close() } }) { Icon(Icons.Filled.Close, null) } }
                    )
                },
            ) { p ->
                LazyColumn(
                    contentPadding = p,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(characters) { character ->
                        Card {
                            ListItem(
                                modifier = Modifier.padding(4.dp),
                                text = { Text(character.name) },
                                secondaryText = {
                                    Column {
                                        Text("Smash: ${character.choices.filter { it.value == Choice.Smash }.count()}")
                                        Text("Pass: ${character.choices.filter { it.value == Choice.Pass }.count()}")
                                        Text("Undecided: ${character.choices.filter { it.value == Choice.Undecided }.count()}")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { scope.launch { state.drawerState.open() } }) { Icon(Icons.Default.Menu, null) } },
                title = { Text("Pokemon") },
                actions = {
                    CustomTooltip(
                        tooltip = { Box(Modifier.padding(10.dp)) { Text("Will Overwrite Current Data") } }
                    ) { OutlinedButton(onClick = { filePicker = true }) { Text("Import CSV Data") } }
                    OutlinedButton(onClick = { saveDialog = true }) { Text("Export Data to CSV") }
                }
            )
        },
        bottomBar = {
            Column {
                Slider(
                    value = location.value.toFloat(),
                    onValueChange = { location.value = it.toInt() },
                    valueRange = 0f..pokemons.size.toFloat(),
                    modifier = Modifier.background(MaterialTheme.colors.surface)
                )
                BottomAppBar {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(onClick = { location.value-- }) { Text("Previous") }
                        Button(onClick = { location.value++ }) { Text("Next") }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding = padding,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.5f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pokemon?.let {
                        items(characters) { p ->
                            var removeCharacter by remember { mutableStateOf(false) }

                            if (removeCharacter) {
                                AlertDialog(
                                    onDismissRequest = { removeCharacter = false },
                                    title = { Text("Remove ${p.name}") },
                                    text = { Text("Are you sure you want to remove this character?") },
                                    confirmButton = { TextButton(onClick = { removeCharacter = !characters.remove(p) }) { Text("Remove") } },
                                    dismissButton = { TextButton(onClick = { removeCharacter = false }) { Text("Cancel") } }
                                )
                            }

                            CharacterContent(p, it) { removeCharacter = true }
                        }
                    }
                    item {
                        var newName by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            isError = characters.any { newName == it.name },
                            modifier = Modifier
                                .onPreviewKeyEvent {
                                    if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) {
                                        if (newName.isNotEmpty() && characters.none { c -> newName == c.name }) {
                                            characters.add(Character(newName))
                                            newName = ""
                                        }
                                        true
                                    } else false
                                }
                                .padding(horizontal = 4.dp),
                            label = { Text("Add Character") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    if (newName.isNotEmpty() && characters.none { newName == it.name }) {
                                        characters.add(Character(newName))
                                        newName = ""
                                    }
                                }) { Icon(Icons.Default.AddCircle, null) }
                            }
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { pokemon?.let { PokemonContent(it, pokemons.size) } }
            }

            AnimatedVisibility(
                dragState,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(modifier = Modifier.matchParentSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Drag-n-Drop to import")
                        Text("Please note that this will overwrite the current data")
                    }
                }
            }
        }
    }
}

// read in csv file and populate characters with choices
fun readFile(file: File, character: SnapshotStateList<Character>) {
    character.clear()
    val lines = file.readLines()
    val chars = lines[0].split(",").drop(1)
    val choices1 = lines.drop(1).map { it.split(",").drop(1) }
    chars.forEach { character.add(Character(it)) }
    choices1.forEachIndexed { index, strings ->
        chars.forEachIndexed { indexC, _ ->
            character[indexC].also { c ->
                c.choices[(index + 1).toString().padStart(3, '0')] = Choice.valueOf(strings[indexC])
            }
        }
    }
}

fun writeToFile(file: File, pokemons: Array<out Pokemon>, vararg character: Character) {
    val f = """
,${character.joinToString(",") { it.name }}
${pokemons.joinToString("\n") { "${it.name},${character.joinToString(",") { c -> "${c.getChoice(it.id)}" }}" }}
                    """.trimIndent()
    if (!file.exists()) file.createNewFile()
    file.writeText(f)
    println(f)
}

@Composable
fun CharacterContent(character: Character, pokemon: Pokemon, onRemove: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, null, tint = Alizarin) }
            Text(character.name)
        }

        val choice = character.choices.getOrDefault(pokemon.id, Choice.Undecided)

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = { character.choices[pokemon.id] = if (character.choices[pokemon.id] == Choice.Smash) Choice.Undecided else Choice.Smash },
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = animateColorAsState(if (choice == Choice.Smash) Emerald else MaterialTheme.colors.surface).value,
                    contentColor = animateColorAsState(if (choice == Choice.Smash) Color(0xFF003918) else MaterialTheme.colors.onSurface).value
                )
            ) { Text("Smash") }

            OutlinedButton(
                onClick = { character.choices[pokemon.id] = if (character.choices[pokemon.id] == Choice.Pass) Choice.Undecided else Choice.Pass },
                colors = ButtonDefaults.outlinedButtonColors(
                    backgroundColor = animateColorAsState(if (choice == Choice.Pass) Alizarin else MaterialTheme.colors.surface).value,
                    contentColor = animateColorAsState(if (choice == Choice.Pass) Color(0xFF680000) else MaterialTheme.colors.onSurface).value
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(pokemon.id + "/$size")
        Text(pokemon.name.capitalize(Locale.current))
        Image(bitmap = loadNetworkImage(pokemon.image_hq), null)
    }
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
    MaterialTheme(darkColors(primary = Color(0xff2196F3))) {
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

enum class FileDialogMode(internal val id: Int) { Load(FileDialog.LOAD), Save(FileDialog.SAVE) }

@Composable
private fun FileDialog(
    mode: FileDialogMode,
    title: String = "Choose a file",
    parent: Frame? = null,
    block: FileDialog.() -> Unit = {},
    onCloseRequest: (result: String?) -> Unit
) = AwtWindow(
    create = {
        object : FileDialog(parent, title, mode.id) {
            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    onCloseRequest(directory + File.separator + file)
                }
            }
        }.apply(block)
    },
    dispose = FileDialog::dispose
)

@ExperimentalFoundationApi
@Composable
fun CustomTooltip(
    backgroundColor: Color = MaterialTheme.colors.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    tooltip: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    TooltipArea(
        tooltip = {
            // composable tooltip content
            Surface(
                modifier = Modifier.shadow(4.dp),
                color = backgroundColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(4.dp)
            ) { tooltip() }
        }
    ) { content() }
}