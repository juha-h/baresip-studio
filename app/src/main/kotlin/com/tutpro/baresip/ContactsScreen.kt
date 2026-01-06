package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import com.tutpro.baresip.CustomElements.AlertDialog
import com.tutpro.baresip.CustomElements.TextAvatar
import com.tutpro.baresip.CustomElements.verticalScrollbar
import java.io.File
import java.io.IOException
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

const val avatarSize: Int = 96

fun NavGraphBuilder.contactsScreenRoute(
    navController: NavController,
    viewModel: ViewModel
) {
    composable("contacts") { _ ->
        ContactsScreen(navController = navController, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsScreen(
    navController: NavController,
    viewModel: ViewModel
) {

    val ctx = LocalContext.current
    var searchContactName by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    )
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.contacts),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.navigateUp() }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            }
        },
        bottomBar = {
            BottomBar(
                navController = navController,
                searchContactName = searchContactName,
                onSearchContactNameChange = { searchContactName = it }
            )
        },
        content = { contentPadding ->
            ContactsContent(ctx, viewModel, navController, contentPadding, searchContactName)
        }
    )
}

@Composable
private fun BottomBar(
    navController: NavController,
    searchContactName: String,
    onSearchContactNameChange: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchContactName,
            onValueChange = {
                onSearchContactNameChange(it)
                if (it.isBlank()) {
                    keyboardController?.hide()
                }
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            trailingIcon = {
                if (searchContactName.isNotEmpty())
                    Icon(
                        Icons.Outlined.Clear,
                        contentDescription = null,
                        modifier = Modifier.clickable {
                            onSearchContactNameChange("")
                            keyboardController?.hide()
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            },
            label = { Text(stringResource(R.string.search)) },
            textStyle = TextStyle(fontSize = 18.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            )
        )
        SmallFloatingActionButton(
            onClick = { navController.navigate("baresip_contact//new") },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                modifier = Modifier.size(36.dp),
                contentDescription = stringResource(R.string.add)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactsContent(
    ctx: Context,
    viewModel: ViewModel,
    navController: NavController,
    contentPadding: PaddingValues,
    searchQuery: String
) {
    val showDialog = remember { mutableStateOf(false) }
    val dialogMessage = remember { mutableStateOf("") }
    val positiveText = remember { mutableStateOf("") }
    val positiveAction = remember { mutableStateOf({}) }
    val neutralText = remember { mutableStateOf("") }
    val neutralAction = remember { mutableStateOf({}) }

    if (showDialog.value)
        AlertDialog(
            showDialog = showDialog,
            title = stringResource(R.string.confirmation),
            message = dialogMessage.value,
            positiveButtonText = positiveText.value,
            onPositiveClicked = positiveAction.value,
            neutralButtonText = neutralText.value,
            onNeutralClicked = neutralAction.value,
            negativeButtonText = stringResource(R.string.cancel)
        )

    val lazyListState = rememberLazyListState()

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            lazyListState.scrollToItem(0)
        }
    }

    val filteredContacts = remember(BaresipService.contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            BaresipService.contacts
        } else {
            BaresipService.contacts.filter { contact ->
                contact.name().contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 10.dp)
            .verticalScrollbar(
                state = lazyListState,
                width = 4.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            ),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(filteredContacts, key = { it.id() }) { contact ->

            val name = contact.name()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                when(contact) {

                    is Contact.BaresipContact -> {
                        val avatarImage = contact.avatarImage
                        if (avatarImage != null)
                            Image(
                                bitmap = avatarImage.asImageBitmap(),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                        else
                            TextAvatar(name, contact.color)
                    }

                    is Contact.AndroidContact -> {
                        val thumbNailUri = contact.thumbnailUri
                        if (thumbNailUri != null)
                            AsyncImage(
                                model = thumbNailUri,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                            )
                        else
                            TextAvatar(name, contact.color)
                    }
                }

                when(contact) {

                    is Contact.BaresipContact -> {
                        Text(text = name,
                            fontSize = 20.sp,
                            fontStyle = if (contact.favorite()) FontStyle.Italic else FontStyle.Normal,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                                .combinedClickable(
                                    onClick = {
                                        val aor = viewModel.selectedAor.value
                                        val ua = UserAgent.ofAor(aor)
                                        val intent = Intent(ctx, MainActivity::class.java)
                                        if (ua != null) {
                                            intent.putExtra("uap", ua.uap)
                                            intent.putExtra("peer", contact.uri)
                                        }
                                        else
                                            Log.w(TAG, "onClickListener did not find UA for $aor")
                                        dialogMessage.value = String.format(
                                            ctx.getString(R.string.contact_action_question),
                                            name
                                        )
                                        positiveText.value = ctx.getString(R.string.call)
                                        positiveAction.value = {
                                            if (ua != null) {
                                                handleIntent(ctx, viewModel, intent, "call")
                                                navController.navigate("main") {
                                                    popUpTo("main")
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                        neutralText.value = ctx.getString(R.string.send_message)
                                        neutralAction.value = {
                                            if (ua != null) {
                                                handleIntent(ctx, viewModel, intent, "message")
                                                navController.navigateUp()
                                            }
                                        }
                                        showDialog.value = true
                                    },
                                    onLongClick = {
                                        dialogMessage.value = String.format(
                                            ctx.getString(R.string.contact_delete_question),
                                            name
                                        )
                                        positiveText.value = ctx.getString(R.string.delete)
                                        positiveAction.value = {
                                            val id = contact.id
                                            val avatarFile = File(
                                                BaresipService.filesPath,
                                                "$id.png"
                                            )
                                            if (avatarFile.exists()) {
                                                try {
                                                    avatarFile.delete()
                                                } catch (e: IOException) {
                                                    Log.e(
                                                        TAG,
                                                        "Could not delete file $id.png: ${e.message}"
                                                    )
                                                }
                                            }
                                            Contact.removeBaresipContact(contact)
                                        }
                                        neutralText.value = ""
                                        showDialog.value = true
                                    }
                                )
                        )
                        SmallFloatingActionButton(
                            modifier = Modifier.padding(end = 10.dp),
                            onClick = { navController.navigate("baresip_contact/${name}/old") },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                modifier = Modifier.size(28.dp),
                                contentDescription = stringResource(R.string.edit)
                            )
                        }
                    }

                    is Contact.AndroidContact -> {
                        Text(text = name,
                            fontSize = 20.sp,
                            fontStyle = if (contact.favorite()) FontStyle.Italic else FontStyle.Normal,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp, top = 4.dp, bottom = 4.dp)
                                .combinedClickable(
                                    onClick = { navController.navigate("android_contact/${name}") },
                                    onLongClick = {
                                        dialogMessage.value = String.format(
                                            ctx.getString(R.string.contact_delete_question),
                                            name
                                        )
                                        positiveText.value = ctx.getString(R.string.delete)
                                        positiveAction.value = {
                                            ctx.contentResolver.delete(
                                                ContactsContract.RawContacts.CONTENT_URI,
                                                ContactsContract.Contacts.DISPLAY_NAME + "='" + name + "'",
                                                null
                                            )
                                        }
                                        neutralText.value = ""
                                        showDialog.value = true
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
