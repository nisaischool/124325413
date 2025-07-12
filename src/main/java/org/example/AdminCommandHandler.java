package org.example;

import org.example.model.AcceptedUser;
import org.example.model.ApplicationData;
import org.example.model.JobPosition;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

public class AdminCommandHandler {

    private final JobBotHandler bot;
    private final DatabaseManager databaseManager;

    private static final String ADMIN_PASSWORD = "1";
    private final Set<Long> adminUsers = new HashSet<>();
    private Map<Long, Map<Integer, Boolean>> adminJobStatusMap = new HashMap<>();

    public AdminCommandHandler(JobBotHandler bot, DatabaseManager databaseManager) {
        this.bot = bot;
        this.databaseManager = databaseManager;
    }

    public void handleAdminCommand(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();

        if (text.equals("/admin")) {
            SendMessage askPassword = new SendMessage(chatId.toString(), "🔑 Iltimos, parolni kiriting:");
            try {
                bot.execute(askPassword);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            bot.getAdminStepMap().put(userId, "WAITING_FOR_PASSWORD");
            return;
        }

        if ("WAITING_FOR_PASSWORD".equals(bot.getAdminStepMap().get(userId))) {
            if (text.equals(ADMIN_PASSWORD)) {
                adminUsers.add(userId);
                bot.getAdminStepMap().remove(userId);

                SendMessage panel = new SendMessage(chatId.toString(), "✅ Admin panelga xush kelibsiz!");
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

                List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("📄 Arizalarni ko‘rish")
                        .callbackData("view_applications")
                        .build()));

                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("✅ Qabul qilinganlar")
                        .callbackData("view_accepted")
                        .build()));

                rows.add(List.of(InlineKeyboardButton.builder()
                        .text("⚙️ Ish o‘rinlarini boshqarish")
                        .callbackData("manage_jobs")
                        .build()));

                markup.setKeyboard(rows);
                panel.setReplyMarkup(markup);

                try {
                    bot.execute(panel);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            } else {
                try {
                    bot.execute(new SendMessage(chatId.toString(), "❌ Noto‘g‘ri parol. Qayta urinib ko‘ring."));
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
            return;
        }

        if (!adminUsers.contains(userId)) {
            try {
                bot.execute(new SendMessage(chatId.toString(), "⛔ Sizda ruxsat yo‘q."));
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }

        String currentStep = bot.getAdminStepMap().get(userId);

        if ("ADDING_JOB_NAME".equals(currentStep)) {
            databaseManager.saveJobPosition(text, null, null, true);

            bot.getTempJobData().remove(userId);
            bot.getAdminStepMap().remove(userId);

            SendMessage savedMsg = new SendMessage(chatId.toString(), "✅ Yangi ish so‘hasi muvaffaqiyatli saqlandi!");
            try {
                bot.execute(savedMsg);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void handleAdminCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        String text = update.hasMessage() && update.getMessage().hasText() ? update.getMessage().getText() : "";

        if (callbackData.equals("view_applications")) {
            List<ApplicationData> applications = databaseManager.getAllApplications();

            if (applications.isEmpty()) {
                SendMessage noApps = new SendMessage(chatId.toString(), "❗ Hozircha hech qanday ariza mavjud emas.");
                try {
                    bot.execute(noApps);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            SendMessage selectUserMsg = new SendMessage(chatId.toString(), "📋 Ko‘rish uchun arizachi nomini tanlang:");
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            for (ApplicationData app : applications) {
                String fullName = app.getFullName();
                if (fullName == null || fullName.trim().isEmpty()) {
                    fullName = "👤 Noma'lum foydalanuvchi";
                }

                InlineKeyboardButton btn = InlineKeyboardButton.builder()
                        .text(fullName)
                        .callbackData("view_application_" + app.getUserId())
                        .build();
                rows.add(List.of(btn));
            }

            markup.setKeyboard(rows);
            selectUserMsg.setReplyMarkup(markup);

            try {
                bot.execute(selectUserMsg);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else if (callbackData.startsWith("view_application_")) {
            userId = Long.parseLong(callbackData.replace("view_application_", ""));

            ApplicationData app = databaseManager.getApplicationByUserId(userId);

            if (app == null) {
                SendMessage noApp = new SendMessage(chatId.toString(), "❗ Bu foydalanuvchi ariza topshirmagan.");
                try {
                    bot.execute(noApp);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("<b>📝 Ariza Ma'lumotlari:</b>\n");

            String jobPositionName = databaseManager.getJobPositionNameById(app.getJobPosition());
            sb.append("💼 <b>Ish o‘rni:</b> ").append(jobPositionName).append("\n\n");

            sb.append("👤 <b>Ism:</b> ").append(app.getFullName()).append("\n");
            sb.append("📞 <b>Telefon:</b> ").append(app.getPhone()).append("\n");
            sb.append("👤 <b>Username:</b> @").append(isEmpty(app.getUsername()) ? "Kiritilmagan" : app.getUsername()).append("\n");
            String certificates = app.getCertificates();
            if (isEmpty(certificates) || certificates.equalsIgnoreCase("bosh")) {
                certificates = "Kiritilmagan";
            }
            sb.append("🏅 <b>Sertifikatlar:</b> ").append(certificates).append("\n");
            sb.append("🏢 <b>Filial:</b> ").append(app.getBranch()).append("\n");

            SendMessage applicationMessage = new SendMessage(chatId.toString(), sb.toString());
            applicationMessage.setParseMode("HTML");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            InlineKeyboardButton cvButton = InlineKeyboardButton.builder()
                    .text("📄 CV")
                    .callbackData("view_cv_" + app.getUserId())
                    .build();
            rows.add(List.of(cvButton));

            InlineKeyboardButton acceptButton = InlineKeyboardButton.builder()
                    .text("✅ Qabul qilish")
                    .callbackData("accept_" + app.getUserId())
                    .build();
            InlineKeyboardButton rejectButton = InlineKeyboardButton.builder()
                    .text("❌ Rad etish")
                    .callbackData("reject_" + app.getUserId())
                    .build();
            rows.add(List.of(acceptButton, rejectButton));

            markup.setKeyboard(rows);
            applicationMessage.setReplyMarkup(markup);

            try {
                bot.execute(applicationMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else if (callbackData.startsWith("accept_")) {
            Long acceptedUserId = Long.parseLong(callbackData.replace("accept_", ""));
            ApplicationData app = databaseManager.getApplicationByUserId(acceptedUserId);

            if (app != null) {
                String fullName = app.getFullName() != null ? app.getFullName() : "Kiritilmagan";
                String phone = app.getPhone() != null ? app.getPhone() : "Kiritilmagan";
                String username = app.getUsername() != null ? app.getUsername() : "no_username";
                String certificates = app.getCertificates() != null ? app.getCertificates() : "Kiritilmagan";
                String branch = app.getBranch() != null ? app.getBranch() : "Kiritilmagan";
                String cvFileId = app.getCvFileId() != null ? app.getCvFileId() : "";
                String jobPosition = app.getJobPosition(); // Already an ID as String

                databaseManager.saveAcceptedApplication(
                        acceptedUserId,
                        fullName,
                        phone,
                        username,
                        certificates,
                        branch,
                        cvFileId,
                        jobPosition
                );

                databaseManager.deleteUserApplication(acceptedUserId);

                EditMessageText confirmedMsg = new EditMessageText();
                confirmedMsg.setChatId(chatId);
                confirmedMsg.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
                confirmedMsg.setText("✅ Ariza qabul qilindi va ro‘yxatga qo‘shildi!");
                try {
                    bot.execute(confirmedMsg);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }

                SendMessage userMessage = new SendMessage();
                userMessage.setChatId(acceptedUserId.toString());
                userMessage.setParseMode("HTML");
                userMessage.setText("""
            🎉 <b>Siz qabul qilindingiz!</b>

            ✅ Sizning arizangiz ko‘rib chiqildi va siz tanlandingiz. Tez orada siz bilan bog‘lanishadi.

            📞 <i>Iltimos, telefoningizni doim yoqilgan holatda saqlang.</i>
            """);

                InlineKeyboardMarkup menuMarkup = new InlineKeyboardMarkup();
                InlineKeyboardButton backToMenuButton = InlineKeyboardButton.builder()
                        .text("🏠 Bosh menyu")
                        .callbackData("back_to_menu")
                        .build();
                menuMarkup.setKeyboard(List.of(List.of(backToMenuButton)));
                userMessage.setReplyMarkup(menuMarkup);

                try {
                    bot.execute(userMessage);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
        } else if (callbackData.startsWith("reject_")) {
            Long rejectedUserId = Long.parseLong(callbackData.replace("reject_", ""));

            databaseManager.deleteUserApplication(rejectedUserId);

            EditMessageText rejectedMsg = new EditMessageText();
            rejectedMsg.setChatId(chatId);
            rejectedMsg.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
            rejectedMsg.setText("❌ Ariza rad etildi va o‘chirildi.");

            try {
                bot.execute(rejectedMsg);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

            SendMessage userMessage = new SendMessage();
            userMessage.setChatId(rejectedUserId.toString());
            userMessage.setParseMode("HTML");
            userMessage.setText("""
        ❗ <b>Afsuski, arizangiz rad etildi.</b>

        ✉️ Biz bilan bog‘langaningiz uchun rahmat! Kelajakda omad tilaymiz.
        """);

            InlineKeyboardMarkup menuMarkup = new InlineKeyboardMarkup();
            InlineKeyboardButton backToMenuButton = InlineKeyboardButton.builder()
                    .text("🏠 Bosh menyu")
                    .callbackData("back_to_menu")
                    .build();
            menuMarkup.setKeyboard(List.of(List.of(backToMenuButton)));
            userMessage.setReplyMarkup(menuMarkup);

            try {
                bot.execute(userMessage);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } else if (callbackData.equals("view_accepted")) {
            List<AcceptedUser> acceptedUsers = databaseManager.getAllAcceptedUsers();

            if (acceptedUsers.isEmpty()) {
                SendMessage noAccepted = new SendMessage(chatId.toString(), "❗ Hozircha hech kim qabul qilinmagan.");
                try {
                    bot.execute(noAccepted);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            SendMessage acceptedListMsg = new SendMessage(chatId.toString(), "✅ Qabul qilinganlar ro‘yxati:");
            InlineKeyboardMarkup acceptedMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> acceptedRows = new ArrayList<>();

            for (AcceptedUser user : acceptedUsers) {
                InlineKeyboardButton btn = InlineKeyboardButton.builder()
                        .text(user.getFullName())
                        .callbackData("accepted_user_" + user.getUserId())
                        .build();
                acceptedRows.add(List.of(btn));
            }

            acceptedMarkup.setKeyboard(acceptedRows);
            acceptedListMsg.setReplyMarkup(acceptedMarkup);

            try {
                bot.execute(acceptedListMsg);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else if (callbackData.startsWith("accepted_user_")) {
            Long selectedUserId = Long.parseLong(callbackData.replace("accepted_user_", ""));
            AcceptedUser accepted = databaseManager.getAcceptedUserById(selectedUserId);

            if (accepted == null) {
                SendMessage notFound = new SendMessage(chatId.toString(), "❗ Bu foydalanuvchi topilmadi.");
                try {
                    bot.execute(notFound);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            String jobName = databaseManager.getJobPositionNameById(accepted.getJobPosition());

            StringBuilder sb = new StringBuilder();
            sb.append("<b>✅ Qabul qilingan foydalanuvchi:</b>\n\n");
            sb.append("💼 <b>Lavozim:</b> ").append(jobName).append("\n\n");
            sb.append("🏢 <b>Filial:</b> ").append(accepted.getBranch() != null ? accepted.getBranch() : "Kiritilmagan").append("\n");
            sb.append("👤 <b>Ismi:</b> ").append(accepted.getFullName() != null ? accepted.getFullName() : "Kiritilmagan").append("\n");
            sb.append("📞 <b>Telefon:</b> ").append(accepted.getPhoneNumber() != null ? accepted.getPhoneNumber() : "Kiritilmagan").append("\n");
            sb.append("🔗 <b>Username:</b> @").append(accepted.getUsername() != null ? accepted.getUsername() : "no_username").append("\n");
            sb.append("🎓 <b>Sertifikat(lar):</b> ").append(accepted.getCertificates() != null ? accepted.getCertificates() : "Kiritilmagan").append("\n");

            SendMessage userInfo = new SendMessage(chatId.toString(), sb.toString());
            userInfo.setParseMode("HTML");

            try {
                bot.execute(userInfo);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

            String cvFileId = accepted.getCvFileId();
            if (cvFileId != null && !cvFileId.isEmpty()) {
                SendDocument cvDoc = new SendDocument();
                cvDoc.setChatId(chatId.toString());
                cvDoc.setDocument(new InputFile(cvFileId));
                cvDoc.setCaption("📄 <b>CV fayli:</b>");
                cvDoc.setParseMode("HTML");
                try {
                    bot.execute(cvDoc);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            } else {
                SendMessage noCv = new SendMessage(chatId.toString(), "❗ CV mavjud emas.");
                noCv.setParseMode("HTML");
                try {
                    bot.execute(noCv);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        } else if (callbackData.equals("manage_jobs")) {
            List<Map<String, Object>> jobs = databaseManager.getAllJobPositionsWithStatus();

            if (jobs.isEmpty()) {
                SendMessage noJobs = new SendMessage(chatId.toString(), "❗ Hozircha ish o‘rinlari mavjud emas.");
                try {
                    bot.execute(noJobs);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            Map<Integer, Boolean> tempStatusMap = new HashMap<>();
            for (Map<String, Object> job : jobs) {
                int id = (int) job.get("id");
                boolean isActive = (boolean) job.get("is_active");
                tempStatusMap.put(id, isActive);
            }
            adminJobStatusMap.put(userId, tempStatusMap);

            SendMessage jobsMessage = new SendMessage(chatId.toString(), "⚙️ <b>Ish o‘rinlarini boshqarish:</b>");
            jobsMessage.setParseMode("HTML");

            InlineKeyboardMarkup markup = generateJobsInlineMarkup(tempStatusMap, jobs);
            jobsMessage.setReplyMarkup(markup);

            try {
                bot.execute(jobsMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else if (callbackData.startsWith("toggle_job_")) {
            int jobId = Integer.parseInt(callbackData.replace("toggle_job_", ""));
            Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

            Map<Integer, Boolean> statusMap = adminJobStatusMap.get(userId);
            if (statusMap != null && statusMap.containsKey(jobId)) {
                boolean current = statusMap.get(jobId);
                statusMap.put(jobId, !current);
            }

            List<Map<String, Object>> jobs = databaseManager.getAllJobPositionsWithStatus();

            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId);
            editMessage.setMessageId(messageId);
            editMessage.setParseMode("HTML");
            editMessage.setText("⚙️ <b>Ish o‘rinlarini boshqarish:</b>");

            InlineKeyboardMarkup markup = generateJobsInlineMarkup(statusMap, jobs);
            editMessage.setReplyMarkup(markup);

            try {
                bot.execute(editMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else if (callbackData.equals("save_jobs")) {
            Map<Integer, Boolean> statusMap = adminJobStatusMap.get(userId);
            if (statusMap != null) {
                for (Map.Entry<Integer, Boolean> entry : statusMap.entrySet()) {
                    databaseManager.updateJobPositionStatus(entry.getKey(), entry.getValue());
                }
            }

            adminJobStatusMap.remove(userId);

            EditMessageText savedMsg = new EditMessageText();
            savedMsg.setChatId(chatId);
            savedMsg.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
            savedMsg.setParseMode("HTML");
            savedMsg.setText("✅ O‘zgartirishlar muvaffaqiyatli saqlandi.");

            try {
                bot.execute(savedMsg);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } else if (callbackData.equals("cancel_jobs")) {
            adminJobStatusMap.remove(userId);

            EditMessageText cancelMsg = new EditMessageText();
            cancelMsg.setChatId(chatId);
            cancelMsg.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
            cancelMsg.setParseMode("HTML");
            cancelMsg.setText("❌ O‘zgartirishlar bekor qilindi.");

            try {
                bot.execute(cancelMsg);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } else if (callbackData.startsWith("view_cv_")) {
            long cvUserId = Long.parseLong(callbackData.replace("view_cv_", ""));
            ApplicationData app = databaseManager.getApplicationByUserId(cvUserId);

            if (app != null && app.getCvFileId() != null && !app.getCvFileId().isEmpty()) {
                String cvFileId = app.getCvFileId();

                SendDocument cvDoc = new SendDocument();
                cvDoc.setChatId(chatId.toString());
                cvDoc.setDocument(new InputFile(cvFileId));
                cvDoc.setCaption("📄 <b>CV</b>");
                cvDoc.setParseMode("HTML");

                try {
                    bot.execute(cvDoc);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            } else {
                SendMessage noCv = new SendMessage(chatId.toString(), "❗ CV ni foydalanuvchi kiritmagan.");
                try {
                    bot.execute(noCv);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        } else if (callbackData.startsWith("view_diploma_")) {
            long diplomaUserId = Long.parseLong(callbackData.replace("view_diploma_", ""));
            ApplicationData app = databaseManager.getApplicationByUserId(diplomaUserId);

            if (app != null && app.getDiplomaFileId() != null && !app.getDiplomaFileId().isEmpty()) {
                String diplomaFileId = app.getDiplomaFileId();

                if (diplomaFileId.startsWith("AgAC") || diplomaFileId.startsWith("BQAC")) {
                    SendPhoto diplomaPhoto = new SendPhoto();
                    diplomaPhoto.setChatId(chatId.toString());
                    diplomaPhoto.setPhoto(new InputFile(diplomaFileId));
                    diplomaPhoto.setCaption("🎓 <b>Diplom</b>");
                    diplomaPhoto.setParseMode("HTML");

                    try {
                        bot.execute(diplomaPhoto);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                } else {
                    SendDocument diplomaDoc = new SendDocument();
                    diplomaDoc.setChatId(chatId.toString());
                    diplomaDoc.setDocument(new InputFile(diplomaFileId));
                    diplomaDoc.setCaption("🎓 <b>Diplom</b>");
                    diplomaDoc.setParseMode("HTML");

                    try {
                        bot.execute(diplomaDoc);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                SendMessage noDiploma = new SendMessage(chatId.toString(), "❗ Diplom faylini foydalanuvchi kiritmagan.");
                try {
                    bot.execute(noDiploma);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        } else if (callbackData.equals("add_new_job")) {
            SendMessage askName = new SendMessage(chatId.toString(), "📝 Iltimos, yangi ish sohasining nomini kiriting:");
            bot.getAdminStepMap().put(userId, "ADDING_JOB_NAME");
            try {
                bot.execute(askName);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private InlineKeyboardMarkup generateJobsInlineMarkup(Map<Integer, Boolean> statusMap, List<Map<String, Object>> jobs) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        jobs.sort(Comparator.comparing(job -> (int) job.get("id")));

        for (Map<String, Object> job : jobs) {
            int id = (int) job.get("id");
            String name = (String) job.get("name");
            boolean active = statusMap.getOrDefault(id, false);

            String emoji = active ? "✅" : "⬜️";
            String buttonText = emoji + " " + name;

            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData("toggle_job_" + id)
                    .build();

            rows.add(List.of(button));
        }

        InlineKeyboardButton addJobButton = InlineKeyboardButton.builder()
                .text("➕ Yangi ish soha qo‘shish")
                .callbackData("add_new_job")
                .build();

        rows.add(List.of(addJobButton));

        InlineKeyboardButton saveButton = InlineKeyboardButton.builder()
                .text("✅ Saqlash")
                .callbackData("save_jobs")
                .build();

        InlineKeyboardButton cancelButton = InlineKeyboardButton.builder()
                .text("❌ Bekor qilish")
                .callbackData("cancel_jobs")
                .build();

        rows.add(List.of(saveButton, cancelButton));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }
}