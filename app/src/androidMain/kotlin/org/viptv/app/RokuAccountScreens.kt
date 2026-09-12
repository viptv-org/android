package org.viptv.app

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONObject
import kotlinx.coroutines.launch

private val AccountCanvas = Color(0xFF101112)
private val AccountWhite = Color(0xFFF5F5F5)
private val AccountMuted = Color(0xFFA6A8AA)
private val AccountSurface = Color(0xFF202224)

@Composable private fun AccountText(text: String, x: Int, y: Int, width: Int = 1096, size: Int = 22, muted: Boolean = false, lines: Int = 1, centered: Boolean = false) {
    Text(text, Modifier.offset(x.dp, y.dp).width(width.dp), color = if (muted) AccountMuted else AccountWhite,
        fontSize = size.sp, fontWeight = if (size >= 32) FontWeight.Bold else FontWeight.Normal,
        maxLines = lines, overflow = TextOverflow.Ellipsis, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
}
@Composable private fun AccountMark() { Image(painterResource(R.drawable.viptv_mark), "VIPTV", Modifier.offset(96.dp,44.dp).size(42.dp,36.dp)) }

@Composable internal fun Pairing(state: AppState, controller: AppController) {
    Box(Modifier.fillMaxSize().background(AccountCanvas)) {
        AccountMark()
        AccountText("Sign in to VIPTV",96,170,size=52)
        AccountText("Visit this address, then enter the code shown below.",96,260,640,32,lines=2)
        AccountText(state.deviceCode?.verificationUri ?: "Preparing secure pairing…",96,364,640,28,lines=2)
        AccountText(state.deviceCode?.userCode.orEmpty(),96,450,640,44)
        state.deviceCode?.let { code ->
            val value = code.verificationUriComplete ?: "${code.verificationUri}?code=${code.userCode}"
            val bitmap = remember(value) { runCatching {
                val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE,250,250)
                Bitmap.createBitmap(250,250,Bitmap.Config.ARGB_8888).apply {
                    for (y in 0 until 250) for (x in 0 until 250) setPixel(x,y,if(matrix[x,y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }.asImageBitmap()
            }.getOrNull() }
            if(bitmap != null) Box(Modifier.offset(886.dp,184.dp).size(298.dp).background(Color.White).padding(24.dp)) {
                Image(bitmap,"Scan to pair VIPTV",Modifier.size(250.dp))
            }
        }
        if(state.message != null) TvButton("Try again",controller::retryAuthentication,Modifier.offset(96.dp,540.dp).size(240.dp,56.dp))
    }
}

@Composable internal fun ProfileChooser(state: AppState, controller: AppController) {
    val shown = state.profiles.drop(state.profilePage*5).take(5)
    val first = remember { FocusRequester() }
    LaunchedEffect(state.profilePage, shown.map { it.id }) { first.requestFocus() }
    Box(Modifier.fillMaxSize().background(AccountCanvas)) {
        AccountText(if(state.managingProfiles) "Manage profiles" else "Who's watching?",100,146,1080,44,centered=true)
        val start = (1280 - (shown.size*178 + (shown.size-1).coerceAtLeast(0)*34))/2
        shown.forEachIndexed { index, profile ->
            var focused by remember(profile.id) { mutableStateOf(false) }
            Column(Modifier.offset((start+index*212).dp,252.dp).size(178.dp,230.dp)) {
                Holdable({ if(state.managingProfiles) controller.editProfile(profile) else controller.chooseProfile(profile) }, { controller.editProfile(profile) },
                    Modifier.size(178.dp).then(if(index==0) Modifier.focusRequester(first) else Modifier).onFocusChanged { focused=it.hasFocus }
                        .border(if(focused) 3.dp else 0.dp,if(focused) AccountWhite else Color.Transparent,RoundedCornerShape(12.dp)).padding(9.dp)) {
                    AccountAvatar(profile.name,profile.avatarUrl,Modifier.size(160.dp))
                }
                Text(profile.name,Modifier.padding(top=3.dp).width(178.dp),color=if(focused) AccountWhite else AccountMuted,fontSize=22.sp,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        val canAdd = state.profiles.size < 12
        Row(Modifier.offset(if(canAdd) 392.dp else 520.dp,530.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            if(canAdd) TvButton("Add profile",{controller.editProfile()},Modifier.size(240.dp,56.dp).then(if(shown.isEmpty()) Modifier.focusRequester(first) else Modifier))
            TvButton(if(state.managingProfiles) "Done" else "Manage profiles",controller::toggleProfileManagement,Modifier.size(240.dp,56.dp))
        }
        if(state.profiles.size>5) {
            TvButton("Previous",{controller.setProfilePage(state.profilePage-1)},Modifier.offset(452.dp,612.dp).size(180.dp,40.dp))
            TvButton("Next",{controller.setProfilePage(state.profilePage+1)},Modifier.offset(648.dp,612.dp).size(180.dp,40.dp))
            AccountText("${state.profilePage+1} / ${(state.profiles.size+4)/5}",860,615,280,19,true)
        }
    }
}

@Composable private fun AccountAvatar(name:String,url:String?,modifier:Modifier=Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF30363C)),contentAlignment=Alignment.Center) {
        Text(name.take(2).uppercase(),color=AccountWhite,fontSize=42.sp,fontWeight=FontWeight.Bold)
        if(!url.isNullOrBlank()) AsyncImage(url,"$name avatar",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
    }
}

private data class AvatarEntry(val name:String,val uri:String)
private data class AvatarCategory(val style:String,val name:String,val entries:List<AvatarEntry>)
@Composable private fun avatarCatalog():List<AvatarCategory> {
    val context=LocalContext.current
    return remember { runCatching {
        val json=context.assets.open("roku/data/avatar-catalog.json").bufferedReader().use { JSONObject(it.readText()) }
        val categories=json.getJSONArray("categories")
        List(categories.length()) { index -> val category=categories.getJSONObject(index); val items=category.optJSONArray("items")
            AvatarCategory(category.getString("style"),category.getString("name"),List(items?.length() ?: 48) { n -> val item=items?.getJSONObject(n)
                AvatarEntry(item?.getString("name") ?: "${category.getString("name")} ${n+1}", if(item!=null) "file:///android_asset/roku/"+item.getString("local").removePrefix("pkg:/") else "file:///android_asset/roku/images/avatar-catalog/${category.getString("style")}-${n+1}.png") })
        }
    }.getOrDefault(emptyList()) }
}

@Composable internal fun ProfileEditor(profile:Profile?,controller:AppController) {
    val state by controller.state.collectAsState()
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var style by remember(profile?.id) { mutableStateOf(profile?.avatarStyle ?: "disney") }
    var choice by remember(profile?.id) { mutableIntStateOf(profile?.avatarChoice ?: 1) }
    var avatarChanged by remember(profile?.id) { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var choosingAvatar by remember { mutableStateOf(false) }
    val catalog=avatarCatalog()
    val currentAvatar=catalog.firstOrNull { it.style==style }?.entries?.getOrNull(choice-1)
    val first=remember { FocusRequester() }
    val avatarFocus=remember { FocusRequester() }
    var returnToAvatar by remember { mutableStateOf(false) }
    LaunchedEffect(editingName,choosingAvatar) { if(!editingName && !choosingAvatar) { if(returnToAvatar) avatarFocus.requestFocus() else first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(AccountCanvas)) {
        AccountMark(); AccountText(if(profile==null) "Add a profile" else "Edit profile",256,148,850,42)
        AccountText("A space for their favorites, shows, and discoveries.",256,222,900,22,true)
        var avatarFocused by remember {mutableStateOf(false)}
        Holdable({returnToAvatar=true;choosingAvatar=true},null,Modifier.offset(256.dp,302.dp).size(176.dp).focusRequester(avatarFocus).onFocusChanged {avatarFocused=it.hasFocus}.border(if(avatarFocused) 3.dp else 0.dp,if(avatarFocused) AccountWhite else Color.Transparent,RoundedCornerShape(12.dp)).padding(4.dp)) {
            AccountAvatar(name,if(avatarChanged || profile==null) currentAvatar?.uri else profile.avatarUrl,Modifier.fillMaxSize())
        }
        AccountText("Change avatar",256,486,176,22,true,centered=true)
        AccountText("PROFILE NAME",464,292,560,22,true)
        TvButton(name.ifBlank { "Enter a name" },{returnToAvatar=false;editingName=true},Modifier.offset(464.dp,338.dp).size(560.dp,64.dp).focusRequester(first))
        AccountText("Select to type with your remote or a connected keyboard.",464,420,560,19,true)
        state.message?.let { AccountText(it,464,484,560,19,lines=3) }
        Row(Modifier.offset(256.dp,574.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            TvButton(if(profile==null) "Create profile" else "Save",{controller.saveProfile(profile,name,style,choice)},Modifier.size(240.dp,56.dp))
            TvButton("Cancel",controller::back,Modifier.size(240.dp,56.dp))
            if(profile!=null && !profile.primary) TvButton("Delete profile",{controller.requestDeleteProfile(profile)},Modifier.size(240.dp,56.dp))
        }
        if(editingName) RokuTextEntry("Name this profile","Enter a name for this viewer.",name,maxLength=80,onDone={name=it.take(80);editingName=false},onCancel={editingName=false})
        if(choosingAvatar) AvatarPicker(catalog,style,choice,{s,c->style=s;choice=c;avatarChanged=true;choosingAvatar=false},{choosingAvatar=false})
    }
}

@Composable private fun AvatarPicker(catalog:List<AvatarCategory>,style:String,choice:Int,onChoose:(String,Int)->Unit,onCancel:()->Unit) {
    var selectedStyle by remember { mutableStateOf(style) }
    var page by remember(selectedStyle) { mutableIntStateOf(0) }
    val category=catalog.firstOrNull { it.style==selectedStyle } ?: catalog.firstOrNull()
    var focusedName by remember { mutableStateOf("") }
    val first=remember(page,selectedStyle) { FocusRequester() }
    val initialCategory=catalog.indexOfFirst {it.style==style}.coerceAtLeast(0)
    val categoriesState=rememberLazyListState(initialFirstVisibleItemIndex=initialCategory)
    val categoryFocus=remember {FocusRequester()}
    var requestGrid by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {if(catalog.isNotEmpty()) categoryFocus.requestFocus()}
    LaunchedEffect(page,selectedStyle,requestGrid) { if(requestGrid && category?.entries?.isNotEmpty()==true) {first.requestFocus();requestGrid=false} }
    BackHandler(onBack=onCancel)
    Box(Modifier.fillMaxSize().background(AccountCanvas)) {
        AccountText("Find your favorite",96,112,size=42);AccountText("${catalog.sumOf {it.entries.size}} avatars. Pick a world, then pick your character.",96,178,size=22,muted=true)
        LazyColumn(Modifier.offset(96.dp,238.dp).size(216.dp,408.dp),state=categoriesState,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            itemsIndexed(catalog) { index, item -> TvButton(item.name,{selectedStyle=item.style;requestGrid=true},Modifier.size(216.dp,44.dp).then(if(index==initialCategory) Modifier.focusRequester(categoryFocus) else Modifier),onFocused={selectedStyle=item.style}) }
        }
        category?.entries?.drop(page*18)?.take(18)?.forEachIndexed { index,item ->
            var focused by remember(item.uri) { mutableStateOf(false) }
            Holdable({onChoose(category.style,page*18+index+1)},null,
                Modifier.offset((360+(index%6)*140).dp,(238+(index/6)*140).dp).size(128.dp)
                    .then(if(index==0) Modifier.focusRequester(first) else Modifier).onFocusChanged { focused=it.hasFocus;if(focused) focusedName=item.name }
                    .border(if(focused) 3.dp else 0.dp,if(focused) AccountWhite else Color.Transparent,RoundedCornerShape(12.dp)).padding(4.dp)) {
                AccountAvatar(item.name,item.uri,Modifier.fillMaxSize())
            }
        }
        val pageCount=((category?.entries?.size ?: 0)+17)/18
        if(pageCount>1) {
            TvButton("Previous",{page=(page+pageCount-1)%pageCount;requestGrid=true},Modifier.offset(360.dp,662.dp).size(180.dp,40.dp))
            TvButton("Next",{page=(page+1)%pageCount;requestGrid=true},Modifier.offset(552.dp,662.dp).size(180.dp,40.dp))
        } else AccountText(focusedName,360,664,440,18)
        Text("${category?.name.orEmpty()}  ·  ${page+1} / $pageCount",Modifier.offset(812.dp,664.dp).width(372.dp),color=AccountMuted,fontSize=18.sp,textAlign=TextAlign.End)
    }
}

/** A full-canvas TV keyboard also accepts ordinary platform/mobile text input. */
@Composable internal fun RokuTextEntry(title:String,instruction:String,initial:String="",secret:Boolean=false,maxLength:Int=256,onDone:(String)->Unit,onCancel:()->Unit) {
    val limit=if(secret) minOf(8,maxLength).coerceAtLeast(0) else maxLength.coerceAtLeast(0)
    var value by remember(title) { mutableStateOf((if(secret) initial.filter(Char::isDigit) else initial).take(limit)) }
    fun append(character:Char) { if(value.length<limit) value+=character }
    val first=remember { FocusRequester() }
    BackHandler(onBack=onCancel)
    LaunchedEffect(title) { first.requestFocus() }
    Box(Modifier.fillMaxSize().background(AccountCanvas)) {
        AccountMark();AccountText(title,180,112,1040,44);AccountText(instruction,180,182,1040,22,true)
        BasicTextField(value,{value=(if(secret) it.filter(Char::isDigit) else it).take(limit)},Modifier.offset(180.dp,242.dp).size(920.dp,48.dp),
            textStyle=TextStyle(color=AccountWhite,fontSize=30.sp),singleLine=true,cursorBrush=SolidColor(AccountWhite),
            keyboardOptions=KeyboardOptions(keyboardType=if(secret) KeyboardType.NumberPassword else KeyboardType.Text),
            visualTransformation=if(secret) PasswordVisualTransformation() else VisualTransformation.None)
        val keys=if(secret) listOf("123","456","789","0") else listOf("abcdefghi","jklmnopqr","stuvwxyz0","123456789")
        keys.forEachIndexed { row,letters -> Row(Modifier.offset(180.dp,(300+row*62).dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            letters.forEachIndexed { column,char -> TvButton(char.toString(),{append(char)},Modifier.size(if(secret) 90.dp else 64.dp,52.dp).then(if(row==0&&column==0) Modifier.focusRequester(first) else Modifier)) }
        } }
        Row(Modifier.offset(860.dp,300.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            TvButton("Delete",{value=value.dropLast(1)},Modifier.size(140.dp,52.dp));TvButton("Clear",{value=""},Modifier.size(140.dp,52.dp))
        }
        if(!secret) {
            TvButton("Space",{append(' ')},Modifier.offset(860.dp,362.dp).size(290.dp,52.dp))
            Row(Modifier.offset(860.dp,424.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ":/.-".forEach { char->TvButton(char.toString(),{append(char)},Modifier.size(66.dp,52.dp)) }
            }
            Row(Modifier.offset(860.dp,486.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                "?=&_".forEach { char->TvButton(char.toString(),{append(char)},Modifier.size(66.dp,52.dp)) }
            }
        }
        Row(Modifier.offset(180.dp,608.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            TvButton(if(secret) "Unlock" else "Done",{val submitted=value; if(secret) value="";onDone(submitted)},Modifier.size(240.dp,56.dp))
            TvButton("Cancel",{value="";onCancel()},Modifier.size(240.dp,56.dp))
        }
    }
}

@Composable internal fun PinDialog(prompt:PinPrompt,controller:AppController,modifier:Modifier=Modifier) {
    val state by controller.state.collectAsState()
    Box(modifier.fillMaxSize()) { RokuTextEntry(prompt.title,state.message ?: "Enter your parent PIN.",secret=true,onDone=controller::submitPin,onCancel=controller::cancelPin) }
}

@Composable internal fun RokuChoiceDialog(title:String,options:List<Pair<String,()->Unit>>,onDismiss:()->Unit) {
    val first=remember(title) { FocusRequester() }
    LaunchedEffect(title) { if(options.isNotEmpty()) first.requestFocus() }
    BackHandler(onBack=onDismiss)
    val visibleCount=options.size.coerceIn(1,7)
    val height=146+visibleCount*62
    Box(Modifier.fillMaxSize().background(Color(0xDC080909)),contentAlignment=Alignment.Center) {
        Box(Modifier.size(880.dp,height.dp).background(Color(0xFF191B1D),RoundedCornerShape(12.dp))) {
            Text(title,Modifier.offset(44.dp,32.dp).size(792.dp,54.dp),color=AccountWhite,fontSize=32.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
            LazyColumn(Modifier.offset(44.dp,94.dp).size(792.dp,(visibleCount*62-10).dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                itemsIndexed(options) { index,option -> TvButton(option.first,option.second,Modifier.size(792.dp,52.dp).then(if(index==0) Modifier.focusRequester(first) else Modifier)) }
            }
        }
    }
}

@Composable internal fun SourcePicker(media:Media,sources:List<Source>,controller:AppController) {
    val state by controller.state.collectAsState()
    var provider by remember(media.id) { mutableStateOf<String?>(null) }
    var filtering by remember { mutableStateOf(false) }
    val filterFocus=remember { FocusRequester() }
    var activeSourceFocus by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(state.dialog) { if(state.dialog==null) activeSourceFocus?.requestFocus() }
    val shown=sources.filter {provider==null || it.provider==provider}
    val first=remember { FocusRequester() }
    var claimed by remember(media.id,provider) { mutableStateOf(false) }
    LaunchedEffect(shown.isNotEmpty(),provider,filtering,state.dialog) { if(shown.isNotEmpty() && !claimed && !filtering && state.dialog==null) {first.requestFocus();claimed=true} }
    Box(Modifier.fillMaxSize().background(AccountCanvas)) {
        AccountText("Choose a source",100,54,size=44);AccountText(media.name,100,119,1096,22,true)
        TvButton((provider ?: "All providers")+"  ▾",{filtering=true},Modifier.offset(100.dp,166.dp).size(256.dp,48.dp).focusRequester(filterFocus))
        AccountText(if(state.loading) "Finding sources" else "${shown.size} sources",384,178,480,19,true)
        AccountText("Hold OK or Menu for details",804,178,350,19,true)
        if(state.loading) CircularProgressIndicator(Modifier.offset(if(sources.isEmpty()) 610.dp else 1172.dp,if(sources.isEmpty()) 294.dp else 162.dp).size(if(sources.isEmpty()) 60.dp else 26.dp),color=AccountWhite,strokeWidth=3.dp)
        if(shown.isEmpty()) {
            AccountText(if(state.loading) "Finding sources" else "No sources available",250,380,780,32,centered=true)
            AccountText(if(state.loading) "Sources appear here as they arrive." else "Try again or choose another provider.",250,436,780,22,true,centered=true)
            if(!state.loading) TvButton("Try again",{controller.chooseSources(media)},Modifier.offset(520.dp,506.dp).size(240.dp,56.dp))
        } else LazyColumn(Modifier.offset(100.dp,234.dp).size(1096.dp,448.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            itemsIndexed(shown,key={_,source->source.id}) { index,source ->
                var focused by remember(source.id) { mutableStateOf(false) }
                val sourceFocus=remember(source.id) {FocusRequester()}
                Holdable({controller.start(media,source)},{controller.requestDialog(DialogKind.SourceDetails,SourceDisplayPolicy.title(source),source=source)},
                    Modifier.size(1096.dp,216.dp).then(if(index==0) Modifier.focusRequester(first) else Modifier).focusRequester(sourceFocus).onFocusChanged {focused=it.hasFocus;if(focused) activeSourceFocus=sourceFocus}
                        .background(if(focused) AccountWhite else AccountSurface,RoundedCornerShape(12.dp))) {
                    Box(Modifier.fillMaxSize()) {
                        val fg=if(focused) AccountCanvas else AccountWhite
                        Text(SourceDisplayPolicy.title(source),Modifier.offset(20.dp,10.dp).width(1056.dp),color=fg,fontSize=22.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(SourceDisplayPolicy.body(source),Modifier.offset(20.dp,46.dp).size(1056.dp,142.dp),color=if(focused) AccountCanvas else Color(0xFFC5C6C7),fontSize=19.sp,lineHeight=23.sp,maxLines=6,overflow=TextOverflow.Ellipsis)
                        Text(listOfNotNull(source.quality,source.audio).joinToString("  ·  "),Modifier.offset(20.dp,190.dp).width(1056.dp),color=fg,fontSize=16.sp,maxLines=1)
                    }
                }
            }
        }
        if(filtering) RokuChoiceDialog("Providers",listOf("All providers" to {provider=null;claimed=false;filtering=false})+sources.map {it.provider}.distinct().map { item->item to {provider=item;claimed=false;filtering=false} },{filtering=false;filterFocus.requestFocus()})
    }
}

@Composable internal fun ActionDialog(dialog:DialogState,controller:AppController,modifier:Modifier=Modifier) {
    val dismiss={controller.dismissDialog()}
    if(dialog.kind==DialogKind.SourceDetails) {
        BackHandler(onBack=dismiss)
        val scroll=rememberScrollState()
        val scope=rememberCoroutineScope()
        val bodyFocus=remember {FocusRequester()}
        LaunchedEffect(dialog.source?.id) {bodyFocus.requestFocus()}
        Box(modifier.fillMaxSize().background(Color(0xF8101112))) {
            AccountText(dialog.title,100,72,1060,36)
            Text(dialog.source?.let { SourceDisplayPolicy.body(it) }.orEmpty(),Modifier.offset(100.dp,158.dp).size(1060.dp,460.dp).verticalScroll(scroll).focusRequester(bodyFocus).onPreviewKeyEvent {event->
                val key=event.nativeKeyEvent
                val delta=when(key.keyCode) {KeyEvent.KEYCODE_DPAD_DOWN->160;KeyEvent.KEYCODE_DPAD_UP->-160;else->0}
                if(delta!=0 && ((delta>0 && scroll.value<scroll.maxValue)||(delta<0 && scroll.value>0))) {
                    if(key.action==KeyEvent.ACTION_DOWN) scope.launch {scroll.animateScrollTo((scroll.value+delta).coerceIn(0,scroll.maxValue))}
                    true
                } else false
            }.focusable(),color=AccountWhite,fontSize=22.sp)
            TvButton("Back",dismiss,Modifier.offset(100.dp,650.dp).size(180.dp,48.dp))
        };return
    }
    val options=buildList<Pair<String,()->Unit>> {
        when(dialog.kind) {
            DialogKind.SignOut -> { add("Keep watching" to dismiss);add("Sign out" to {controller.dismissDialog();controller.signOut()}) }
            DialogKind.DeleteProfile -> {add("Cancel" to dismiss);add("Delete profile" to {dialog.profile?.let {controller.deleteProfile(it)};controller.dismissDialog()})}
            DialogKind.QueueManage -> {dialog.media?.let { media ->
                if(QueuePolicy.canResume(media)) add("Resume" to {controller.resumeQueueItem(media)})
                add("Choose source" to {controller.chooseQueueSource(media)});add("Remove from Continue Watching" to {controller.removeFromQueue(media)})
            };add("Cancel" to dismiss)}
            DialogKind.QueueRemoved -> {add("Undo" to {dialog.media?.let {controller.undoQueueRemoval(it)}});add("Done" to dismiss)}
            DialogKind.MyListManage,DialogKind.LiveManage -> {dialog.media?.let {media->add("Remove from My List" to {controller.toggleMyList(media);controller.dismissDialog()})};add("Cancel" to dismiss)}
            DialogKind.EpisodeManage -> { dialog.media?.let { media ->
                add("Mark watched" to {controller.correctEpisode(media,true);controller.dismissDialog()});add("Mark unwatched" to {controller.correctEpisode(media,false);controller.dismissDialog()})
                add("Watch from beginning" to {controller.dismissDialog();controller.chooseSources(media,false)})
            };add("Cancel" to dismiss)}
            DialogKind.NextUnavailable -> {dialog.media?.let {media->add("Open series" to {controller.dismissDialog();controller.open(media)})};add("Done" to dismiss)}
            DialogKind.PlaybackRecovery -> {add("Retry" to {controller.retryPlaybackRecovery()});add("Choose source" to {controller.chooseAnotherSourceForRecovery()});add("Back" to {controller.backFromPlaybackRecovery()})}
            else -> add("Done" to dismiss)
        }
    }
    Box(modifier.fillMaxSize()) {RokuChoiceDialog(dialog.title,options,dismiss)}
}

@Composable internal fun AddonsScreen(state:AppState,controller:AppController) {
    RokuAddons(state.addons,controller::installAddon,controller::toggleAddon,controller::removeAddon,controller::back)
}
