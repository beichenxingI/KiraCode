package com.kirakira.kiracode.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kirakira.kiracode.ui.common.CardTransferBus
import com.kirakira.kiracode.ui.workspace.WorkspaceScreen
import com.kirakira.kiracode.ui.workspace.WorkspaceViewModel

/**
 * 导航路由常量：仅保留主工作台（全部功能经浮窗完成）。
 */
object NavigationRoutes {
    const val WORKSPACE = "workspace"

    const val SESSION_ID = "session_id"
}

// ==================== 页面转场（唯一路由下实际不触发，保留以防扩展） ====================

private const val PushMillis = 240

/**
 * 应用导航宿主：入口与唯一目的地均为 [WorkspaceScreen]（Agent 工作台）。
 * 角色卡一律通过 [CardTransferBus] 内存传递（大卡不进路由）。
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavigationRoutes.WORKSPACE
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            androidx.compose.animation.slideInHorizontally(
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.85f,
                    stiffness = 320f
                )
            ) { it / 3 } + androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(220)
            )
        },
        exitTransition = {
            androidx.compose.animation.slideOutHorizontally(
                animationSpec = androidx.compose.animation.core.tween(200)
            ) { -it / 3 } + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(180)
            )
        },
        popEnterTransition = {
            androidx.compose.animation.slideInHorizontally(
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.85f,
                    stiffness = 320f
                )
            ) { -it / 3 } + androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(220)
            )
        },
        popExitTransition = {
            androidx.compose.animation.slideOutHorizontally(
                animationSpec = androidx.compose.animation.core.tween(200)
            ) { it / 3 } + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(180)
            )
        }
    ) {
        // ============ Agent 工作台（唯一目的地） ============
        composable(
            route = "${NavigationRoutes.WORKSPACE}?${NavigationRoutes.SESSION_ID}={${NavigationRoutes.SESSION_ID}}",
            arguments = listOf(
                navArgument(NavigationRoutes.SESSION_ID) { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong(NavigationRoutes.SESSION_ID) ?: -1

            val viewModel = hiltViewModel<WorkspaceViewModel>()
            // 从总线取出待设为工作卡的卡（Splash 引导等入口）
            val pendingWorkingCard = CardTransferBus.workingCard
            CardTransferBus.workingCard = null

            WorkspaceScreen(
                viewModel = viewModel,
                initialSessionId = if (sessionId == -1L) null else sessionId,
                initialWorkingCard = pendingWorkingCard
            )
        }
    }
}
