package cz.raadost.service.messanger;

import static cz.raadost.service.messanger.Commands.*;

import cz.raadost.service.content.Content;
import cz.raadost.service.content.ContentEntity;
import java.util.ArrayList;
import java.util.List;

import cz.raadost.service.localization.Localization;
import cz.raadost.service.owner.Bot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Service
@PropertySource("classpath:telegram.properties")
@RequiredArgsConstructor
public class Messanger extends TelegramLongPollingBot {

  private final Content content;
  private final Bot bot;
  private final Localization localization;


  @Override
  public String getBotToken() {
    return bot.getBotEntity().getBotToken();
  }

  @Override
  public String getBotUsername() {
    return bot.getBotEntity().getBotName();
  }



  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {
      String messageText = update.getMessage().getText();
      Long chatId = update.getMessage().getChatId();
      User user = update.getMessage().getFrom();
      log.info(user.toString());
      // ADMIN
      if (isAdmin(user.getUserName())) {
        handleCustomAdminMessages(messageText, chatId, user);
      }
      // ANY USER
      switch (messageText) {
        case IS_ADMIN:
          sendMessage(chatId, String.valueOf(isAdmin(user.getUserName())));
          break;
        case START_COMMAND:
          sendMessage(chatId, localization.getWelcome());
          break;
        case ALL_COMMAND:
          sendMessage(chatId, localization.getPickContent());
          sendContentListMessage(chatId, "");
          break;
        case VIDEO_COMMAND:
          sendMessage(chatId, localization.getPickContent());
          sendContentListMessage(chatId, "Video");
          break;
        case SPECIAL_COMMAND:
          sendMessage(chatId, localization.getPickContent());
          sendContentListMessage(chatId, "Special");
          break;
        case BUNDLE_COMMAND:
          sendMessage(chatId, localization.getPickContent());
          sendContentListMessage(chatId, "Bundle");
          break;
        case PHOTOS_COMMAND:_COMMAND:
          sendMessage(chatId, localization.getPickContent());
          sendContentListMessage(chatId, "Photo");
          break;
        default:
          handleCustomUserMessages(messageText, chatId, user);
          break;
      }
    }
  }
  private boolean isAdmin(String userName) {
    return bot.getBotEntity().getAdminUsers().contains(userName);
  }
  //ADMIN
  private void handleCustomAdminMessages(String messageText, Long chatId, User user) {
    if (messageText.equals("/DISPLAY_BOT_DETAILS")) {
      sendMessage(chatId,bot.display());
      return;
    }
    if (messageText.equals("/CHANGE_LANGUAGE")) {
      localization.changeLocalization();
      sendMessage(chatId,"Language changed to - " + localization.getLocalization());
      return;
    }
    if (isRemoveCommand(messageText)) {
      var operationMessage = content.remove(getLongFromString(messageText));
      sendMessage(chatId, operationMessage);
      return;
    }
    if (isDisplayCommand(messageText)) {
      var operationMessage = content.display(getLongFromString(messageText));
      sendMessage(chatId, operationMessage);
      return;
    }
    if (isEditCommand(messageText)) {
      var operationMessage = content.edit(messageText);
      sendMessage(chatId,operationMessage);
      return;
    }
    if (isUpdateBotDetailsCommand(messageText)) {
      var operationMessage = bot.edit(messageText);
      sendMessage(chatId,operationMessage);
      return;
    }
    if (isAddCommand(messageText)) {
      var operationMessage = content.add(messageText);
      sendMessage(chatId,operationMessage);
      return;
    }
  }

  // USER
  private void handleCustomUserMessages(String messageText, Long chatId, User user) {
    if (isNumberCommand(messageText)) {
      sendSpecificContentMessage(messageText, chatId, user);
      return;
    }
    if (isPaidCommand(messageText)) {
      sendUserPaidMessage(messageText, chatId, user);
      return;
    }
    if(!isAdmin(user.getUserName())) {
    sendMessage(chatId, localization.getInvalidRequest());
    }
  }

  private void sendSpecificContentMessage(String messageText, Long chatId, User user) {
    var messageNumber = extractLongFromCommand(messageText,NUMBER_COMMAND);
    if (content.findById(messageNumber) != null) {
      sendMessage(
          chatId, buildContentMessageFromStringIndex(String.valueOf(messageNumber), user.getId()),true);
    } else {
      sendMessage(chatId, localization.getContentOutOfBounds());
    }
  }

  private void sendUserPaidMessage(String messageText, Long chatId, User user) {
    var requestedData = content.findById(extractLongFromCommand(messageText,PAID_COMMAND));
    if (requestedData != null) {
      var data = requestedData;
      String username = user.getUserName();
      var operatorActionMessage = localization.getContactUser();
      if (username == null) {
        var userToContact = bot.getBotEntity().getAdminUsers().get(0);
        var message = String.format(localization.getNoUsername(),userToContact);
        sendMessage(chatId, message);
        operatorActionMessage = localization.getUserWillContactYou();
      } else {
        sendMessage(chatId, String.format(localization.getThanks(),bot.getBotEntity().getSellerName()));
      }
      String usernameDisplay = (username == null) ? "nemá vypněné" : "@" + username;
      String channelMessageText =
          String.format(
              localization.getNotificationDetails(),
              usernameDisplay,
              data.getId(),
              data.getName(),
              data.getPrice(),
              user.getId(),
              operatorActionMessage);
      sendMessage(Long.parseLong(bot.getBotEntity().getNotificationChannel()), channelMessageText);
    }
  }

  private void sendContentListMessage(Long chatId, String filter) {
    List<ContentEntity> content = this.content.getData(filter);
    int batchSize = 30; // Maximum number of items per message
    List<List<ContentEntity>> batches = new ArrayList<>();
    // Split the content into batches
    for (int i = 0; i < content.size(); i += batchSize) {
      int end = Math.min(i + batchSize, content.size());
      batches.add(content.subList(i, end));
    }
    // Send each batch as a separate message
    for (List<ContentEntity> batch : batches) {
      StringBuilder sb = new StringBuilder();
      for (ContentEntity data : batch) {
        sb.append(
            String.format(
                "/%s - %s - %s CZK\n", data.getId(), data.getName(), data.getPrice()));
      }
      if (!sb.isEmpty()) sendMessage(chatId, sb.toString());
    }
  }

  private String buildContentMessageFromStringIndex(String index, Long userId) {
    var botEntity = bot.getBotEntity();
    var selectedData = content.findById(Long.parseLong(index));
    var contentSelected = localization.getContentSelected();
    var contentName = selectedData.getName();
    var contentType = selectedData.getType();
    var contentDescription = selectedData.getDescription();
    var contentPrice = selectedData.getPrice();
    var payment1 = botEntity.getPaymentMethod1();
    var payment2 = botEntity.getPaymentMethod2();
    var paymentGuide = localization.getPaymentGuide();

    var paymentCommand = "/ZAPLACENO_" + selectedData.getId();

    return String.format(
        localization.getContentDetails(),
        contentSelected,
        contentName,
        contentType,
        contentDescription,
        contentPrice,
            payment1,
            payment2,
        userId,
        paymentGuide,
        paymentCommand);
  }
  // ADMIN
  private void sendMessage(Long chatId, String messageText,boolean disableWebPreview) {
    if(messageText.isEmpty()){
      return;
    }
    SendMessage message = new SendMessage();
    message.setChatId(chatId.toString());
    message.setText(messageText);
    message.setDisableWebPagePreview(disableWebPreview);
    try {
      execute(message);
    } catch (TelegramApiException e) {
      e.printStackTrace();
    }
  }

  private void sendMessage(Long chatId, String messageText) {
    if(messageText.isEmpty()){
      return;
    }
    SendMessage message = new SendMessage();
    message.setChatId(chatId.toString());
    message.setText(messageText);

    try {
      execute(message);
    } catch (TelegramApiException e) {
      e.printStackTrace();
    }
  }
}
