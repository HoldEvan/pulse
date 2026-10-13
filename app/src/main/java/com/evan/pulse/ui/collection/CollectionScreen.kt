package com.evan.pulse.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evan.pulse.R
import com.evan.pulse.data.model.CollectionInfo
import com.evan.pulse.data.model.CollectionTree
import com.evan.pulse.data.model.SavedRequest
import com.evan.pulse.data.model.httpMethod
import com.evan.pulse.data.model.targetUrl
import com.evan.pulse.ui.common.BottomBarClearance
import com.evan.pulse.ui.common.DialogActions
import com.evan.pulse.ui.common.MethodBadge
import com.evan.pulse.ui.common.roundedPressable
import com.evan.pulse.ui.common.subPageScrollState
import com.evan.pulse.ui.spec.SpecImportScreen
import com.evan.pulse.ui.common.subPageOverScroll
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 集合页：用面包屑逐层进入集合。
 *
 * 外层是一级集合，点进去是二级，以此类推；不再做展开折叠。当前所在集合的重命名与删除
 * 挂在面包屑右侧的工具位上，根层级没有当前集合，因此那两个按钮不显示。
 *
 * 改层级（例如把三级集合降成二级）走「移动到」弹窗：长按集合名或点行首的拖拽把手，
 * 在候选父级里选一个，候选已排除自身与全部后代，因此不会出现移进自己子树的情况。
 *
 * 导航状态就是 [rawPath]：从根到当前集合的 id 链。空列表表示根层级。
 * 删除或移动之后这条链可能指向已经不存在的节点，所以每次组合都按真实父子关系重新收敛。
 */
@Composable
fun CollectionScreen(
    modifier: Modifier = Modifier,
    onSwitchTab: (Int) -> Unit,
    viewModel: CollectionsViewModel = viewModel()
) {
    val collections by viewModel.collections.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    val projects by viewModel.projects.collectAsState()

    var rawPath by remember { mutableStateOf(emptyList<Long>()) }
    var moveFolderId by remember { mutableStateOf<Long?>(null) }
    var moveSelected by remember { mutableStateOf<Long?>(null) }
    var moveFailed by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var renameOpen by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf("") }
    var deleteOpen by remember { mutableStateOf(false) }
    var renameRequestId by remember { mutableStateOf<Long?>(null) }
    var renameRequestName by remember { mutableStateOf("") }
    var moveRequestId by remember { mutableStateOf<Long?>(null) }
    var moveRequestSelected by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(currentProjectId) {
        rawPath = emptyList()
        moveFolderId = null
        moveSelected = null
        moveFailed = false
        moveRequestId = null
        moveRequestSelected = null
        renameRequestId = null
    }

    val own = viewModel.projectCollections()
    val path = remember(own, rawPath) { sanitizePath(own, rawPath) }
    val stack = remember(own, path) { path.mapNotNull { id -> own.firstOrNull { it.id == id } } }
    val current = stack.lastOrNull()

    val children = viewModel.childrenOf(current?.id)
    val ownRequests = requests.filter { it.collectionId == current?.id }

    val rootLabel = projects.firstOrNull { it.id == currentProjectId }?.name
        ?: stringResource(R.string.tab_collection)
    val items = remember(stack, rootLabel) {
        buildList {
            add(BreadcrumbItem(path = rootLabel))
            stack.forEach { folder -> add(BreadcrumbItem(path = folder.name)) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .subPageOverScroll()
            .verticalScroll(subPageScrollState())
            .padding(horizontal = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BreadcrumbBar(
                    items = items,
                    onItemClick = { index ->
                        rawPath = if (index <= 0) emptyList<Long>() else stack.take(index).map { it.id }
                    },
                    modifier = Modifier.weight(1f),
                    highlightIndex = stack.size
                )
                current?.let { folder ->
                    RowAction(
                        label = stringResource(R.string.collection_move_to),
                        icon = Icons.Filled.DragIndicator,
                        onClick = {
                            moveFolderId = folder.id
                            moveSelected = null
                            moveFailed = false
                        }
                    )
                    RowAction(
                        label = stringResource(R.string.cd_rename),
icon = MiuixIcons.Light.Edit,
                        onClick = {
                            renameName = folder.name
                            renameOpen = true
                        }
                    )
                    RowAction(
                        label = stringResource(R.string.cd_delete),
                        icon = MiuixIcons.Light.Delete,
                        onClick = { deleteOpen = true }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (children.isEmpty() && ownRequests.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (current == null) R.string.collection_empty
                            else R.string.collection_folder_empty
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                children.forEach { child ->
                    FolderRow(
                        folder = child,
                        onOpen = { rawPath = path + child.id },
                        onMoveClick = {
                            moveFolderId = child.id
                            moveSelected = null
                            moveFailed = false
                        }
                    )
                }
                ownRequests.forEach { request ->
                    RequestRow(
                        request = request,
                        onLoad = { viewModel.load(request, onSwitchTab) },
                        onRename = {
                            renameRequestName = request.name
                            renameRequestId = request.id
                        },
                        onMove = {
                            moveRequestSelected = current?.id
                            moveRequestId = request.id
                        },
                        onDelete = { viewModel.deleteRequest(request.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(
                text = stringResource(
                    if (current == null) R.string.collection_new_title else R.string.collection_new_child
                ),
                onClick = {
                    createName = ""
                    showCreate = true
                },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = stringResource(R.string.collection_import_spec),
                onClick = { showImport = true },
                modifier = Modifier.weight(1f)
            )
        }
        BottomBarClearance()
    }

    if (showCreate) {
        OverlayDialog(
            show = true,
            title = stringResource(
                if (current == null) R.string.collection_new_title else R.string.collection_new_child
            ),
            onDismissRequest = { showCreate = false }
        ) {
            Column {
                TextField(
                    value = createName,
                    onValueChange = { createName = it },
                    label = stringResource(R.string.collection_name_label),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                DialogActions(
                    onCancel = { showCreate = false },
                    confirmText = stringResource(R.string.action_save),
                    onConfirm = {
                        if (createName.isNotBlank()) {
                            viewModel.create(createName, current?.id)
                        }
                        showCreate = false
                    }
                )
            }
        }
    }

    val renameTarget = if (renameOpen) current else null
    renameTarget?.let { target ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.collection_rename),
            onDismissRequest = { renameOpen = false }
        ) {
            Column {
                TextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = stringResource(R.string.collection_name_label),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                DialogActions(
                    onCancel = { renameOpen = false },
                    confirmText = stringResource(R.string.action_save),
                    onConfirm = {
                        viewModel.rename(target.id, renameName)
                        renameOpen = false
                    }
                )
            }
        }
    }

    val deleteTarget = if (deleteOpen) current else null
    deleteTarget?.let { target ->
        val doomedFolders = viewModel.descendantFolderCount(target.id)
        val doomedRequests = viewModel.descendantRequestCount(target.id)
        OverlayDialog(
            show = true,
            title = stringResource(R.string.collection_delete),
            onDismissRequest = { deleteOpen = false }
        ) {
            Column {
                Text(
                    text = stringResource(
                        R.string.collection_delete_confirm,
                        doomedFolders,
                        doomedRequests
                    )
                )
                DialogActions(
                    onCancel = { deleteOpen = false },
                    confirmText = stringResource(R.string.action_delete),
                    onConfirm = {
                        viewModel.deleteRecursive(target.id)
                        deleteOpen = false
                    }
                )
            }
        }
    }

    val moveFolder = moveFolderId?.let { id -> own.firstOrNull { it.id == id } }
    moveFolder?.let { folder ->
        val targets = remember(own, folder) { moveTargets(own, folder) }
        OverlayDialog(
            show = true,
            title = stringResource(R.string.collection_move_to),
            onDismissRequest = { moveFolderId = null }
        ) {
            Column {
                Text(
                    text = folder.name,
                    modifier = Modifier.padding(vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MoveTargetRow(
                        label = stringResource(R.string.collection_root_level),
                        depth = 0,
                        selected = moveSelected == null,
                        onClick = { moveSelected = null }
                    )
                    targets.forEach { target ->
                        MoveTargetRow(
                            label = target.label,
                            depth = target.depth,
                            selected = moveSelected == target.parentId,
                            onClick = { moveSelected = target.parentId }
                        )
                    }
                }
                if (moveFailed) {
                    Text(
                        text = stringResource(R.string.collection_move_forbidden),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                DialogActions(
                    onCancel = {
                        moveFolderId = null
                        moveSelected = null
                        moveFailed = false
                    },
                    confirmText = stringResource(R.string.collection_move_to),
                    onConfirm = {
                        val moved = viewModel.move(folder.id, moveSelected)
                        if (moved) {
                            moveFolderId = null
                            moveSelected = null
                            moveFailed = false
                        } else {
                            moveFailed = true
                        }
                    }
                )
            }
        }
    }

    val moveRequest = moveRequestId?.let { id -> requests.firstOrNull { it.id == id } }
    moveRequest?.let { target ->
        val targets = remember(own) { requestTargets(own) }
        OverlayDialog(
            show = true,
            title = stringResource(R.string.collection_move_to),
            onDismissRequest = { moveRequestId = null }
        ) {
            Column {
                Text(
                    text = target.name,
                    modifier = Modifier.padding(vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    targets.forEach { candidate ->
                        MoveTargetRow(
                            label = candidate.label,
                            depth = candidate.depth,
                            selected = moveRequestSelected == candidate.parentId,
                            onClick = { moveRequestSelected = candidate.parentId }
                        )
                    }
                }
                DialogActions(
                    onCancel = {
                        moveRequestId = null
                        moveRequestSelected = null
                    },
                    confirmText = stringResource(R.string.collection_move_to),
                    onConfirm = {
                        val selected = moveRequestSelected
                        if (selected != null) {
                            viewModel.moveRequest(target.id, selected)
                        }
                        moveRequestId = null
                        moveRequestSelected = null
                    }
                )
            }
        }
    }

    val renameRequest = renameRequestId?.let { id -> requests.firstOrNull { it.id == id } }
    renameRequest?.let { target ->
        OverlayDialog(
            show = true,
            title = stringResource(R.string.collection_rename),
            onDismissRequest = { renameRequestId = null }
        ) {
            Column {
                TextField(
                    value = renameRequestName,
                    onValueChange = { renameRequestName = it },
                    label = stringResource(R.string.request_name_label),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                DialogActions(
                    onCancel = { renameRequestId = null },
                    confirmText = stringResource(R.string.action_save),
                    onConfirm = {
                        viewModel.renameRequest(target.id, renameRequestName)
                        renameRequestId = null
                    }
                )
            }
        }
    }

    if (showImport) {
        SpecImportScreen(onDismiss = { showImport = false })
    }
}

/**
 * 移动候选行：按层级缩进，选中项打勾。
 *
 * 缩进用固定宽度的 Spacer 逐个堆叠，避免 Dp 与 Int 相乘。
 */
@Composable
private fun MoveTargetRow(
    label: String,
    depth: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(depth) { Spacer(modifier = Modifier.width(16.dp)) }
        Icon(
            imageVector = if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 同级集合行：点整行进入下一级，长按打开移动弹窗 */
@Composable
private fun FolderRow(
    folder: CollectionInfo,
    onOpen: () -> Unit,
    onMoveClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = MiuixIcons.Light.Folder,
            contentDescription = null,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text = folder.name,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onMoveClick
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = MiuixIcons.Light.ChevronForward,
            contentDescription = null,
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

/**
 * 当前集合下的单个接口条目行。
 *
 * 左侧先显示协议分类（HTTP/WS/MQTT/SSE），HTTP 条目再跟一个方法徽章（GET/POST 这些），
 * 两截并排时仍能看出这条接口属于哪个协议；右侧三个图标动作分别是改名、移到其他集合、删除。
 */
@Composable
private fun RequestRow(
    request: SavedRequest,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    val url = remember(request.data) { request.targetUrl() }
    val method = remember(request.type, request.data) { request.httpMethod() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = request.type.name,
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        method?.let { m -> MethodBadge(m, modifier = Modifier.padding(end = 8.dp)) }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onLoad)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = request.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (url.isNotEmpty()) {
                Text(
                    text = url,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )
            }
        }
        OverlayIconDropdownMenu(
            entries = listOf(
                DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = stringResource(R.string.cd_rename),
                            icon = { m ->
                                Icon(
                                    imageVector = MiuixIcons.Light.Edit,
                                    contentDescription = null,
                                    modifier = m
                                )
                            },
                            onClick = onRename
                        ),
                        DropdownItem(
                            text = stringResource(R.string.cd_move_request),
                            icon = { m ->
                                Icon(
                                    imageVector = Icons.Filled.DragIndicator,
                                    contentDescription = null,
                                    modifier = m
                                )
                            },
                            onClick = onMove
                        ),
                        DropdownItem(
                            text = stringResource(R.string.action_delete),
                            icon = { m ->
                                Icon(
                                    imageVector = MiuixIcons.Light.Delete,
                                    contentDescription = null,
                                    modifier = m
                                )
                            },
                            onClick = onDelete
                        )
                    )
                )
            ),
            minWidth = 28.dp,
            minHeight = 28.dp,
            modifier = Modifier
                .size(28.dp)
                .offset(x = 4.dp)
        ) {
            Icon(
                imageVector = MiuixIcons.Light.More,
                contentDescription = stringResource(R.string.cd_more),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 小图标按钮：图标加无障碍文案，配圆角按压反馈 */
@Composable
private fun RowAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = Modifier
            .roundedPressable(onClick = onClick)
            .padding(4.dp)
    )
}

/** 移动候选：父级 id、展示用的完整路径名链、层级深度 */
private data class MoveTarget(val parentId: Long, val label: String, val depth: Int)

/**
 * 列出候选父级：排除自身与全部后代，并按完整路径名链显示，便于区分不同层级里的同名集合。
 */
private fun moveTargets(collections: List<CollectionInfo>, folder: CollectionInfo): List<MoveTarget> {
    val banned = CollectionTree.descendantsOf(collections, folder.id) + folder.id
    return collections
        .filter { it.id !in banned }
        .map { candidate ->
            val names = CollectionTree.pathOf(collections, candidate.id)
            MoveTarget(candidate.id, names.joinToString(" / "), names.size - 1)
        }
}

/**
 * 列出接口的移动候选：当前项目内全部集合，不做排除。
 *
 * 文件夹移动必须排除自身子树，接口没有子树概念，因此候选是全量的；
 * 允许移回自己所在的集合，等于没有移动。
 */
private fun requestTargets(collections: List<CollectionInfo>): List<MoveTarget> =
    collections.map { candidate ->
        val names = CollectionTree.pathOf(collections, candidate.id)
        MoveTarget(candidate.id, names.joinToString(" / "), names.size - 1)
}

/**
 * 按真实父子关系收敛路径：链上任何一环已经不存在、或者不再是前一环的孩子时从这里截断。
 *
 * 删除集合或跨项目移动之后不会留下指向悬空节点的路径。
 */
private fun sanitizePath(collections: List<CollectionInfo>, rawPath: List<Long>): List<Long> {
    val byId = collections.associateBy { it.id }
    val chain = mutableListOf<Long>()
    for (id in rawPath) {
        val folder = byId[id] ?: break
        val parent = chain.lastOrNull()
        if (parent == null) {
            if (folder.parentId != null) break
        } else if (folder.parentId != parent) {
            break
        }
        chain += id
    }
    return chain
}
