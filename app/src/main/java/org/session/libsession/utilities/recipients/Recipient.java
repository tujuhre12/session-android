/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013 - 2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.session.libsession.utilities.recipients;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;
import org.session.libsession.avatars.ContactColors;
import org.session.libsession.avatars.ContactPhoto;
import org.session.libsession.avatars.GroupRecordContactPhoto;
import org.session.libsession.avatars.ProfileContactPhoto;
import org.session.libsession.avatars.TransparentContactPhoto;
import org.session.libsession.messaging.MessagingModuleConfiguration;
import org.session.libsession.messaging.contacts.Contact;
import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.FutureTaskListener;
import org.session.libsession.utilities.GroupRecord;
import org.session.libsession.utilities.GroupUtil;
import org.session.libsession.utilities.ListenableFutureTask;
import org.session.libsession.utilities.MaterialColor;
import org.session.libsession.utilities.ProfilePictureModifiedEvent;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.UsernameUtils;
import org.session.libsession.utilities.Util;
import org.session.libsession.utilities.recipients.RecipientProvider.RecipientDetails;
import org.session.libsignal.utilities.Log;
import org.session.libsignal.utilities.guava.Optional;
import org.thoughtcrime.securesms.database.model.NotifyType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;

import kotlinx.coroutines.flow.Flow;

public class Recipient implements RecipientModifiedListener, Cloneable {

  private static final String            TAG      = Recipient.class.getSimpleName();
  private static final RecipientProvider provider = new RecipientProvider();

  private final Set<RecipientModifiedListener> listeners = Collections.newSetFromMap(new WeakHashMap<RecipientModifiedListener, Boolean>());

  private final @NonNull Address address;
  private final @NonNull List<Recipient> participants = new LinkedList<>();

  private final     Context context;
  private @Nullable String  name;
  private           boolean resolving;
  private           boolean isLocalNumber;

  private @Nullable Long                 groupAvatarId;
  public            long                 mutedUntil              = 0;
  @NotifyType
  public            int                  notifyType              = 0;
  private           boolean              autoDownloadAttachments = false;
  private           boolean              blocked                 = false;
  private           boolean              approved                = false;
  private           boolean              approvedMe              = false;

  private @Nullable byte[]         profileKey;
  private @Nullable String         profileName;
  private @Nullable String         profileAvatar;
  private           String         notificationChannel;
  private           boolean        blocksCommunityMessageRequests;

  @SuppressWarnings("ConstantConditions")
  public static @NonNull Flow<RecipientV2> from(@NonNull Context context, @NonNull Address address, boolean asynchronous) {
    if (address == null) throw new AssertionError(address);
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("ConstantConditions")
  public static @NonNull Flow<RecipientV2> from(@NonNull Context context, @NonNull Address address, @NonNull Optional<RecipientSettings> settings, @NonNull Optional<GroupRecord> groupRecord, boolean asynchronous) {
    if (address == null) throw new AssertionError(address);
    throw new UnsupportedOperationException();
  }

  public static void applyCached(@NonNull Address address, Consumer<Recipient> consumer) {
    Optional<Recipient> recipient = provider.getCached(address);
    if (recipient.isPresent()) consumer.accept(recipient.get());
  }

  public static boolean removeCached(@NonNull Address address) {
    return provider.removeCached(address);
  }

  Recipient(@NonNull  Context context,
            @NonNull  Address address,
            @Nullable Recipient stale,
            @NonNull  Optional<RecipientDetails> details,
            @NonNull  ListenableFutureTask<RecipientDetails> future)
  {
    this.context   = context.getApplicationContext();
    this.address   = address;
    this.resolving = true;

    if (stale != null) {
      this.name                   = stale.name;
      this.groupAvatarId          = stale.groupAvatarId;
      this.isLocalNumber          = stale.isLocalNumber;
      this.mutedUntil             = stale.mutedUntil;
      this.blocked                = stale.blocked;
      this.approved               = stale.approved;
      this.approvedMe             = stale.approvedMe;
      this.notificationChannel    = stale.notificationChannel;
      this.profileKey             = stale.profileKey;
      this.profileName            = stale.profileName;
      this.profileAvatar          = stale.profileAvatar;
      this.notifyType             = stale.notifyType;
      this.autoDownloadAttachments = stale.autoDownloadAttachments;

      this.participants.clear();
      this.participants.addAll(stale.participants);
    }

    if (details.isPresent()) {
      this.name                    = details.get().name;
      this.groupAvatarId           = details.get().groupAvatarId;
      this.isLocalNumber           = details.get().isLocalNumber;
      this.mutedUntil              = details.get().mutedUntil;
      this.blocked                 = details.get().blocked;
      this.approved                = details.get().approved;
      this.approvedMe              = details.get().approvedMe;
      this.notificationChannel     = details.get().notificationChannel;
      this.profileKey              = details.get().profileKey;
      this.profileName             = details.get().profileName;
      this.profileAvatar           = details.get().profileAvatar;
      this.notifyType              = details.get().notifyType;
      this.autoDownloadAttachments = details.get().autoDownloadAttachments;
      this.blocksCommunityMessageRequests = details.get().blocksCommunityMessageRequests;

      this.participants.clear();
      this.participants.addAll(details.get().participants);
    }

    future.addListener(new FutureTaskListener<RecipientDetails>() {
      @Override
      public void onSuccess(RecipientDetails result) {
        if (result != null) {
          synchronized (Recipient.this) {
            Recipient.this.name                   = result.name;
            Recipient.this.groupAvatarId          = result.groupAvatarId;
            Recipient.this.isLocalNumber          = result.isLocalNumber;
            Recipient.this.mutedUntil             = result.mutedUntil;
            Recipient.this.blocked                = result.blocked;
            Recipient.this.approved               = result.approved;
            Recipient.this.approvedMe             = result.approvedMe;
            Recipient.this.notificationChannel    = result.notificationChannel;
            Recipient.this.profileKey             = result.profileKey;
            Recipient.this.profileName            = result.profileName;
            Recipient.this.profileAvatar          = result.profileAvatar;
            Recipient.this.notifyType             = result.notifyType;
            Recipient.this.autoDownloadAttachments = result.autoDownloadAttachments;
            Recipient.this.blocksCommunityMessageRequests = result.blocksCommunityMessageRequests;


            Recipient.this.participants.clear();
            Recipient.this.participants.addAll(result.participants);
            Recipient.this.resolving = false;

            if (!listeners.isEmpty()) {
              for (Recipient recipient : participants) recipient.addListener(Recipient.this);
            }

            Recipient.this.notifyAll();
          }

          notifyListeners();
        }
      }

      @Override
      public void onFailure(ExecutionException error) {
        Log.w(TAG, error);
      }
    });
  }

  Recipient(@NonNull Context context, @NonNull Address address, @NonNull RecipientDetails details) {
    this.context                = context.getApplicationContext();
    this.address                = address;
    this.name                   = details.name;
    this.groupAvatarId          = details.groupAvatarId;
    this.isLocalNumber          = details.isLocalNumber;
    this.mutedUntil             = details.mutedUntil;
    this.notifyType             = details.notifyType;
    this.autoDownloadAttachments = details.autoDownloadAttachments;
    this.blocked                = details.blocked;
    this.approved               = details.approved;
    this.approvedMe             = details.approvedMe;
    this.notificationChannel    = details.notificationChannel;
    this.profileKey             = details.profileKey;
    this.profileName            = details.profileName;
    this.profileAvatar          = details.profileAvatar;
    this.blocksCommunityMessageRequests = details.blocksCommunityMessageRequests;

    this.participants.addAll(details.participants);
    this.resolving    = false;
  }

  public boolean isLocalNumber() {
    return isLocalNumber;
  }

  public synchronized @NonNull String getName() {
    UsernameUtils usernameUtils = MessagingModuleConfiguration.getShared().getUsernameUtils();
    String accountID = this.address.toString();
    if (isGroupOrCommunityRecipient()) {
      if (this.name == null) {
        List<String> names = new LinkedList<>();
        for (Recipient recipient : participants) {
          names.add(recipient.name);
        }
        return Util.join(names, ", ");
      } else {
        return this.name;
      }
    } else if (isCommunityInboxRecipient()){
      String inboxID = GroupUtil.getDecodedOpenGroupInboxAccountId(accountID);
      return usernameUtils.getContactNameWithAccountID(inboxID, null, Contact.ContactContext.OPEN_GROUP);
    } else {
      return usernameUtils.getContactNameWithAccountID(accountID, null, Contact.ContactContext.REGULAR);
    }
  }

  public void setName(@Nullable String name) {
    boolean notify = false;

    synchronized (this) {
      if (!Util.equals(this.name, name)) {
        this.name = name;
        notify = true;
      }
    }

    if (notify) notifyListeners();
  }

  public boolean getBlocksCommunityMessageRequests() {
    return blocksCommunityMessageRequests;
  }

  public void setBlocksCommunityMessageRequests(boolean blocksCommunityMessageRequests) {
    synchronized (this) {
      this.blocksCommunityMessageRequests = blocksCommunityMessageRequests;
    }

    notifyListeners();
  }

  public synchronized @NonNull MaterialColor getColor() {
    if      (isGroupOrCommunityRecipient()) return MaterialColor.GROUP;
    else if (name != null)       return ContactColors.generateFor(name);
    else                         return ContactColors.UNKNOWN_COLOR;
  }

  public @NonNull Address getAddress() {
    return address;
  }

  public synchronized @Nullable String getProfileName() {
    return profileName;
  }

  public void setProfileName(@Nullable String profileName) {
    synchronized (this) {
      this.profileName = profileName;
    }

    notifyListeners();
  }

  public synchronized @Nullable String getProfileAvatar() {
    return profileAvatar;
  }

  public void setProfileAvatar(@Nullable String profileAvatar) {
    synchronized (this) {
      this.profileAvatar = profileAvatar;
    }

    notifyListeners();
    EventBus.getDefault().post(new ProfilePictureModifiedEvent(this));
  }

  public boolean isGroupOrCommunityRecipient() {
    return address.isGroupOrCommunity();
  }

  public boolean isContactRecipient() {
    return address.isContact();
  }
  public boolean is1on1() { return address.isContact() && !isLocalNumber; }

  public boolean isCommunityRecipient() {
    return address.isCommunity();
  }

  public boolean isCommunityOutboxRecipient() {
    return address.isCommunityOutbox();
  }

  public boolean isCommunityInboxRecipient() {
    return address.isCommunityInbox();
  }

  public boolean isLegacyGroupRecipient() {
    return address.isLegacyGroup();
  }

  public boolean isGroupRecipient() {
    return address.isGroup();
  }

  public boolean isGroupV2Recipient() {
    return address.isGroupV2();
  }


  @Deprecated
  public boolean isPushGroupRecipient() {
    return address.isGroupOrCommunity();
  }

  public @NonNull synchronized List<Recipient> getParticipants() {
    return new LinkedList<>(participants);
  }

  public void setParticipants(@NonNull List<Recipient> participants) {
    synchronized (this) {
      this.participants.clear();
      this.participants.addAll(participants);
    }

    notifyListeners();
  }

  public synchronized void addListener(RecipientModifiedListener listener) {
    if (listeners.isEmpty()) {
      for (Recipient recipient : participants) recipient.addListener(this);
    }
    listeners.add(listener);
  }

  public synchronized void removeListener(RecipientModifiedListener listener) {
    listeners.remove(listener);

    if (listeners.isEmpty()) {
      for (Recipient recipient : participants) recipient.removeListener(this);
    }
  }

  public synchronized @NonNull Drawable getFallbackContactPhotoDrawable(Context context, boolean inverted) {
    return (new TransparentContactPhoto()).asDrawable(context, getColor().toAvatarColor(context), inverted);
  }

  public synchronized @Nullable ContactPhoto getContactPhoto() {
    if      (isLocalNumber)                               return new ProfileContactPhoto(address, String.valueOf(TextSecurePreferences.getProfileAvatarId(context)));
    else if (isGroupOrCommunityRecipient() && groupAvatarId != null) return new GroupRecordContactPhoto(address, groupAvatarId);
    else if (profileAvatar != null)                       return new ProfileContactPhoto(address, profileAvatar);
    else                                                  return null;
  }

  public void setGroupAvatarId(@Nullable Long groupAvatarId) {
    boolean notify = false;

    synchronized (this) {
      if (!Util.equals(this.groupAvatarId, groupAvatarId)) {
        this.groupAvatarId = groupAvatarId;
        notify = true;
      }
    }

    if (notify) notifyListeners();
  }

  @Nullable
  public synchronized Long getGroupAvatarId() {
    return groupAvatarId;
  }

  public synchronized boolean isMuted() {
    return System.currentTimeMillis() <= mutedUntil;
  }

  public void setMuted(long mutedUntil) {
    synchronized (this) {
      this.mutedUntil = mutedUntil;
    }

    notifyListeners();
  }

  public void setNotifyType(@NotifyType int notifyType) {
    synchronized (this) {
      this.notifyType = notifyType;
    }

    notifyListeners();
  }

  public boolean getAutoDownloadAttachments() {
    return autoDownloadAttachments;
  }

  public void setAutoDownloadAttachments(boolean autoDownloadAttachments) {
    synchronized (this) {
      this.autoDownloadAttachments = autoDownloadAttachments;
    }

    notifyListeners();
  }

  public synchronized boolean isBlocked() {
    return blocked;
  }

  public void setBlocked(boolean blocked) {
    synchronized (this) {
      this.blocked = blocked;
    }

    notifyListeners();
  }

  public synchronized boolean isApproved() {
    return approved;
  }

  public void setApproved(boolean approved) {
    synchronized (this) {
      this.approved = approved;
    }

    notifyListeners();
  }

  public synchronized boolean hasApprovedMe() {
    return approvedMe;
  }

  public void setHasApprovedMe(boolean approvedMe) {
    synchronized (this) {
      this.approvedMe = approvedMe;
    }

    notifyListeners();
  }

  public synchronized @Nullable String getNotificationChannel() {
    return notificationChannel;
  }

  public void setNotificationChannel(@Nullable String value) {
    boolean notify = false;

    synchronized (this) {
      if (!Util.equals(this.notificationChannel, value)) {
        this.notificationChannel = value;
        notify = true;
      }
    }

    if (notify) notifyListeners();
  }

  public synchronized @Nullable byte[] getProfileKey() {
    return profileKey;
  }

  public void setProfileKey(@Nullable byte[] profileKey) {
    synchronized (this) {
      this.profileKey = profileKey;
    }

    notifyListeners();
  }

  public synchronized Recipient resolve() {
    while (resolving) Util.wait(this, 0);
    return this;
  }

  public synchronized boolean showCallMenu() {
    return !isGroupOrCommunityRecipient() && hasApprovedMe() && isApproved();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Recipient recipient = (Recipient) o;
    return resolving == recipient.resolving
            && mutedUntil == recipient.mutedUntil
            && notifyType == recipient.notifyType
            && blocked == recipient.blocked
            && approved == recipient.approved
            && approvedMe == recipient.approvedMe
            && address.equals(recipient.address)
            && Objects.equals(name, recipient.name)
            && Objects.equals(groupAvatarId, recipient.groupAvatarId)
            && Arrays.equals(profileKey, recipient.profileKey)
            && Objects.equals(profileName, recipient.profileName)
            && Objects.equals(profileAvatar, recipient.profileAvatar)
            && blocksCommunityMessageRequests == recipient.blocksCommunityMessageRequests;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(
            address,
            name,
            resolving,
            groupAvatarId,
            mutedUntil,
            notifyType,
            blocked,
            approved,
            approvedMe,
            profileName,
            profileAvatar,
            blocksCommunityMessageRequests
    );
    result = 31 * result + Arrays.hashCode(profileKey);
    return result;
  }

  public void notifyListeners() {
    Set<RecipientModifiedListener> localListeners;

    synchronized (this) {
      localListeners = new HashSet<>(listeners);
    }

    for (RecipientModifiedListener listener : localListeners)
      listener.onModified(this);
  }

  @Override
  public void onModified(Recipient recipient) {
    notifyListeners();
  }

  public synchronized boolean isResolving() {
    return resolving;
  }

  public enum DisappearingState {
    LEGACY(0), UPDATED(1);

    private final int id;

    DisappearingState(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public static DisappearingState fromId(int id) {
      return values()[id];
    }
  }

  public static class RecipientSettings {
    private final boolean                blocked;
    private final boolean                approved;
    private final boolean                approvedMe;
    private final long                   muteUntil;
    private final int                    notifyType;
    @Nullable
    private final Boolean                autoDownloadAttachments;
    private final int                    expireMessages;
    private final byte[]                 profileKey;
    private final String                 systemDisplayName;
    private final String                 signalProfileName;
    private final String                 signalProfileAvatar;
    private final String                 notificationChannel;
    private final boolean                blocksCommunityMessageRequests;

    public RecipientSettings(boolean blocked, boolean approved, boolean approvedMe, long muteUntil,
                             int notifyType,
                             @Nullable Boolean autoDownloadAttachments,
                             int expireMessages,
                             @Nullable byte[] profileKey,
                             @Nullable String systemDisplayName,
                             @Nullable String signalProfileName,
                             @Nullable String signalProfileAvatar,
                             @Nullable String notificationChannel,
                             boolean blocksCommunityMessageRequests
    )
    {
      this.blocked                 = blocked;
      this.approved                = approved;
      this.approvedMe              = approvedMe;
      this.muteUntil               = muteUntil;
      this.notifyType              = notifyType;
      this.autoDownloadAttachments = autoDownloadAttachments;
      this.expireMessages          = expireMessages;
      this.profileKey              = profileKey;
      this.systemDisplayName       = systemDisplayName;
      this.signalProfileName       = signalProfileName;
      this.signalProfileAvatar     = signalProfileAvatar;
      this.notificationChannel     = notificationChannel;
      this.blocksCommunityMessageRequests = blocksCommunityMessageRequests;
    }

    public boolean isBlocked() {
      return blocked;
    }

    public boolean isApproved() {
      return approved;
    }

    public boolean hasApprovedMe() {
      return approvedMe;
    }

    public long getMuteUntil() {
      return muteUntil;
    }

    @NotifyType
    public int getNotifyType() {
      return notifyType;
    }

    public boolean getAutoDownloadAttachments() {
      return autoDownloadAttachments;
    }

    public int getExpireMessages() {
      return expireMessages;
    }

    public @Nullable byte[] getProfileKey() {
      return profileKey;
    }

    public @Nullable String getSystemDisplayName() {
      return systemDisplayName;
    }

    public @Nullable String getProfileName() {
      return signalProfileName;
    }

    public @Nullable String getProfileAvatar() {
      return signalProfileAvatar;
    }

    public @Nullable String getNotificationChannel() {
      return notificationChannel;
    }

    public boolean getBlocksCommunityMessageRequests() {
      return blocksCommunityMessageRequests;
    }

  }

  @NonNull
  @Override
  public Recipient clone() throws CloneNotSupportedException {
    return (Recipient) super.clone();
  }
}
