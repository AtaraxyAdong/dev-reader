package com.github.yuyuanweb.mianshiyaplugin.manager;

import cn.hutool.core.util.StrUtil;
import com.github.yuyuanweb.mianshiyaplugin.config.ApiConfig;
import com.github.yuyuanweb.mianshiyaplugin.config.GlobalState;
import com.github.yuyuanweb.mianshiyaplugin.constant.KeyConstant;
import com.github.yuyuanweb.mianshiyaplugin.model.response.User;
import com.github.yuyuanweb.mianshiyaplugin.utils.PanelUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.github.yuyuanweb.mianshiyaplugin.model.common.BaseResponse;
import retrofit2.Response;
import org.cef.network.CefCookieManager;

import javax.swing.*;
import java.io.IOException;

/**
 * Cookie 管理器
 *
 * @author pine
 */
public class CookieManager {

    private static final Logger logger = Logger.getInstance(CookieManager.class);

    /**
     * 获取当前登录用户
     */
    public static User getLoginUser() {
        try {
            Response<BaseResponse<User>> response = ApiConfig.mianShiYaApi.getLoginUser().execute();
            BaseResponse<User> body = response.body();
            if (!response.isSuccessful() || body == null || body.getCode() != 0) {
                logger.warn("Login verification failed: HTTP " + response.code()
                        + ", code " + (body == null ? "absent" : body.getCode()));
                return null;
            }
            User user = body.getData();
            return user != null && user.getId() != null ? user : null;
        } catch (IOException e) {
            logger.warn("Login verification failed: network");
        } catch (RuntimeException e) {
            logger.warn("Login verification failed: invalid response");
        }
        return null;
    }

    public static void handleCookie(CefCookieManager cefCookieManager, String currentUrl, Runnable afterLogin) {

        // 建议使用 visitUrlCookies 而不是 visitAllCookies，更加精准
        // 如果没有 currentUrl，还是可以用 visitAllCookies，但必须改 return 逻辑
        cefCookieManager.visitUrlCookies(currentUrl, true, (cefCookie, count, total, boolRef) -> {
            String targetName = "SESSION";
            String name = cefCookie.name;


            // 如果名字不对，或者值为空，返回 true (继续找下一个)
            if (!targetName.equals(name) || StrUtil.isBlank(cefCookie.value)) {
                return true; // 【关键修改】继续遍历
            }

            // --- 找到 SESSION 了 ---

            GlobalState globalState = GlobalState.getInstance();
            String oldCookie = globalState.getSavedCookie();
            String newCookie = targetName + "=" + cefCookie.value;

            // 如果 Cookie 没变，停止遍历 (或者继续遍历也没事，看你需求)
            if (oldCookie.equals(newCookie)) {
                // 找到了但没变，可以选择停止遍历
                return false;
            }

            // 保存新 Cookie
            globalState.saveCookie(newCookie);

            // 获取用户信息逻辑
            User loginUser = CookieManager.getLoginUser();
            if (loginUser != null) {
                globalState.saveUser(loginUser);
                ActionManager actionManager = ActionManager.getInstance();
                DefaultActionGroup actionGroup = (DefaultActionGroup) actionManager.getAction(KeyConstant.ACTION_BAR);

                // UI 操作务必在 EDT 线程
                SwingUtilities.invokeLater(() -> {
                    PanelUtil.modifyActionGroupWhenLogin(actionGroup, loginUser);
                    afterLogin.run();
                });

                // 找到了并处理成功，停止遍历
                return false;
            } else {
                globalState.removeSavedCookie();
                globalState.removeSavedUser();
            }

            // 如果获取用户失败，可能 Session 无效，继续找下一个（极少情况）或者停止
            return true;
        });
    }

}
