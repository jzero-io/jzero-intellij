package io.jzero.action;

import io.jzero.actionx.FileAction;
import io.jzero.contsant.Constant;
import io.jzero.io.IO;
import io.jzero.notification.Notification;
import io.jzero.ui.FileChooseDialog;
import io.jzero.util.Exec;
import io.jzero.util.FileReload;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class RpcAction extends FileAction {
    @Override
    public void performed(@NotNull AnActionEvent e, @NotNull VirtualFile file, @NotNull Project project) {
        String path = file.getPath();
        try {
            String parent = file.getParent().getPath();
            String content = IO.read(file.getInputStream());
            FileChooseDialog dialog = new FileChooseDialog("zRPC Generate Option", "Cancel", content.contains("import"), true);
            dialog.setDefaultPath(parent);
            dialog.setOnClickListener(new FileChooseDialog.OnClickListener() {
                @Override
                public void onOk(String goctlHome, String output, String protoPath, String style, boolean group, boolean client) {
                    generateRpc(project, goctlHome, protoPath, path, output, style, group, client, e);
                }

                @Override
                public void onJump() {
                }
            });
            dialog.showAndGet();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private void generateRpc(Project project, String goctlHome, String protoPath, String src, String target, String style, boolean group, boolean client, AnActionEvent e) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "generating rpc ...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                File srcFile = new File(src);
                String protoDir = srcFile.getParent();
                String command = "rpc protoc -I " + protoDir;
                if (!StringUtil.isEmptyOrSpaces(protoPath) && !protoPath.equals(protoDir)) {
                    command += " -I " + protoPath;
                }
                command += " " + src + " --style " + style + " --zrpc_out " + target + " --go_out " + target + " --go-grpc_out " + target;
                if (!StringUtil.isEmptyOrSpaces(goctlHome)) {
                    File file = new File(goctlHome);
                    if (!file.exists()) {
                        Notification.getInstance().warning(project, "goctlHome " + goctlHome + " is not exists");
                    } else {
                        if (file.isDirectory()) {
                            command += " --home " + goctlHome;
                        } else {
                            Notification.getInstance().warning(project, "goctlHome " + goctlHome + " is not a directory");
                        }
                    }
                }
                if (group) {
                    command += " --multiple ";
                }
                if (!client) {
                    command += " --client=false ";
                }
                boolean done = Exec.runGoctl(project, command);
                if (done) {
                    FileReload.reloadFromDisk(e);
                    Notification.getInstance().notify(project, "generate rpc done");
                }
            }
        });
    }

    @Override
    public String getExtension() {
        return Constant.RPC_EXTENSION;
    }
}
