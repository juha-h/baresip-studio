package com.tutpro.baresip

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.compose.AsyncImage

fun NavGraphBuilder.androidContactScreenRoute(navController: NavController, viewModel: ViewModel) {
    composable(
        route = "android_contact/{name}",
        arguments = listOf(navArgument("name") { type = NavType.StringType })
    ) { backStackEntry ->
        val ctx = LocalContext.current
        val name = backStackEntry.arguments?.getString("name")!!
        ContactScreen(
            ctx = ctx,
            viewModel = viewModel,
            navController = navController,
            name = name
        )
    }
}

@Composable
private fun ContactScreen(ctx: Context, viewModel: ViewModel, navController: NavController, name: String) {
    val contact = Contact.androidContact(name)
    if (contact == null) {
        Log.e(TAG, "No Android contact found with name $name")
        navController.popBackStack()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = LocalCustomColors.current.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalCustomColors.current.background)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            ) {
                TopAppBar(name, navController)
            }
        },
        content = { contentPadding ->
            ContactContent(ctx, viewModel, navController, contentPadding, contact!!)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(title: String, navController: NavController) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = LocalCustomColors.current.primary,
            navigationIconContentColor = LocalCustomColors.current.onPrimary,
            titleContentColor = LocalCustomColors.current.onPrimary,
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        }
    )
}

@Composable
private fun ContactContent(
    ctx: Context,
    viewModel: ViewModel,
    navController: NavController,
    contentPadding: PaddingValues,
    contact: Contact.AndroidContact
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalCustomColors.current.background)
            .padding(contentPadding)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 52.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(contact)
        ContactName(contact.name)
        Uris(ctx, viewModel, navController, contact)
    }
}

@Composable
private fun TextAvatar(text: String, color: Int) {
    Box(
        modifier = Modifier.size(avatarSize.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(SolidColor(Color(color)))
        }
        Text(text, fontSize = 72.sp, color = Color.White)
    }
}

@Composable
private fun ImageAvatar(uri: Uri) {
    AsyncImage(
        model = uri,
        contentDescription = stringResource(R.string.avatar_image),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(avatarSize.dp).clip(CircleShape)
    )
}

@Composable
private fun Avatar(contact: Contact.AndroidContact) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val color = contact.color
        val name = contact.name
        val thumbnailUri = contact.thumbnailUri
        if (thumbnailUri != null)
            ImageAvatar(thumbnailUri)
        else
            TextAvatar(if (name == "") "" else name[0].toString(), color)
    }
}

@Composable
private fun ContactName(name: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(name, fontSize = 24.sp, color = LocalCustomColors.current.itemText)
    }
}

@Composable
private fun Uris(
    ctx: Context,
    viewModel: ViewModel,
    navController: NavController,
    contact: Contact.AndroidContact
) {
    val lazyListState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp)
            .background(LocalCustomColors.current.background),
        state = lazyListState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(contact.uris) { uri ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uri.substringAfter(":"),
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    color = LocalCustomColors.current.itemText,
                )
                Image(
                    painter = painterResource(R.drawable.message),
                    colorFilter = ColorFilter.tint(LocalCustomColors.current.itemText),
                    contentDescription = "Send Message",
                    modifier = Modifier.padding(end = 24.dp).clickable {
                        val aor = viewModel.selectedAor.value
                        val ua = UserAgent.ofAor(aor)
                        if (ua == null)
                            Log.w(TAG, "message clickable did not find AoR $aor")
                        else {
                            val intent = Intent(ctx, MainActivity::class.java)
                            intent.putExtra("uap", ua.uap)
                            intent.putExtra("peer", uri)
                            handleIntent(ctx, viewModel, intent, "message")
                            navController.navigate("main") {
                                popUpTo("main") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                )
                Image(
                    painter = painterResource(R.drawable.call_small),
                    colorFilter = ColorFilter.tint(LocalCustomColors.current.itemText),
                    contentDescription = "Call",
                    modifier = Modifier.clickable {
                        val aor = viewModel.selectedAor.value
                        val ua = UserAgent.ofAor(aor)
                        if (ua == null)
                            Log.w(TAG, "message clickable did not find AoR $aor")
                        else {
                            val intent = Intent(ctx, MainActivity::class.java)
                            intent.putExtra("uap", ua.uap)
                            intent.putExtra("peer", uri)
                            handleIntent(ctx, viewModel, intent, "call")
                            navController.navigate("main") {
                                popUpTo("main") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    }
}


