/**
 * SPDX-License-Identifier: MIT
 * Copyright 2024 Mercedes-Benz Group China Ltd.
 */

package android.car.bluetooth.lib;

import static android.car.bluetooth.lib.MBBluetoothClass.Device.INVALID_CLASS;
import static android.car.bluetooth.lib.MBBluetoothClass.Device.Major.PERIPHERAL;
import static android.car.bluetooth.lib.MBBluetoothClass.Device.PERIPHERAL_GAME_CONTROLLER;
import static android.car.bluetooth.lib.MBBluetoothClass.Device.PERIPHERAL_JOY_STICK;
import static android.car.bluetooth.lib.MBBluetoothUtils.BluetoothNode.SECONDARY;
import static android.car.bluetooth.lib.MBBluetoothUtils.getDeviceDebugInfo;
import static android.car.bluetooth.lib.MBBluetoothClass.Device.Major.AUDIO_VIDEO;
import static android.car.bluetooth.lib.MBBluetoothClass.Device.Major.UNCATEGORIZED;
import static android.car.bluetooth.lib.MBBluetoothUtils.BluetoothNode.PRIMARY;
import static android.car.bluetooth.lib.MBBluetoothUtils.getBondStateName;
import static android.car.bluetooth.lib.MBBluetoothUtils.getConnectionStateName;
import static android.car.bluetooth.lib.common.Constants.CAR_LINE_PROP;
import static android.car.bluetooth.lib.common.Constants.DEFAULT_CONNECTED_NUMBER;
import static android.car.bluetooth.lib.common.Constants.MAXIMUM_CONNECTED_NUMBER;
import static android.car.bluetooth.lib.common.Constants.RSU_CAR_LINE;
import static android.car.bluetooth.lib.common.Constants.TYPE_GAMECONTROLLER;
import static android.car.bluetooth.lib.common.Constants.TYPE_HEADSET;
import static android.car.bluetooth.lib.common.Constants.TYPE_INVALID;

import android.annotation.IntDef;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapterCommon;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.car.bluetooth.MBCarBluetoothManager;
import android.car.bluetooth.lib.MBBluetoothUtils.BluetoothNode;
import android.car.bluetooth.lib.pairing.factory.PairingStrategyFactory;
import android.car.bluetooth.lib.pairing.strategy.PairingStrategy;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.text.TextUtils;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author: wang shuai
 */
@SuppressLint("MissingPermission")
public class MBBluetoothDevice implements Comparable<MBBluetoothDevice> {
    private static final Logger LOG = new Logger(MBBluetoothDevice.class);

    private static final int INVALID_VALUE = -1;

    /**
     * Indicates the remote device is not bonded (paired).
     * <p>There is no shared link key with the remote device, so communication
     * (if it is allowed at all) will be unauthenticated and unencrypted.
     */
    public static final int BOND_NONE = BluetoothDevice.BOND_NONE;
    /**
     * Indicates bonding (pairing) is in progress with the remote device.
     */
    public static final int BOND_BONDING = BluetoothDevice.BOND_BONDING;
    /**
     * Indicates the remote device is bonded (paired).
     * <p>A shared link keys exists locally for the remote device, so
     * communication can be authenticated and encrypted.
     * <p><i>Being bonded (paired) with a remote device does not necessarily
     * mean the device is currently connected. It just means that the pending
     * procedure was completed at some earlier time, and the link key is still
     * stored locally, ready to use on the next connection.
     * </i>
     */
    public static final int BOND_BONDED = BluetoothDevice.BOND_BONDED;

    private static final long MAX_UUID_DELAY_FOR_AUTO_CONNECT = 5000;
    private static final long MAX_HOGP_DELAY_FOR_AUTO_CONNECT = 30000;
    private static final long MAX_MEDIA_PROFILE_CONNECT_DELAY = 60000;

    public static final int PAIRING_SUCCESS = 0;

    public static final int PAIRING_FAILURE = 1;

    public static final int PAIRING_ERROR_LIMIT_REACHED = 2;

    /**
     * {@see BluetoothDevice.UNBOND_REASON_AUTH_REJECTED}
     */
    public static final int UNBOND_REASON_AUTH_REJECTED =
            BluetoothDevice.UNBOND_REASON_AUTH_REJECTED;

    /**
     * {@see BluetoothDevice.UNBOND_REASON_REMOTE_DEVICE_DOWN}
     */
    public static final int UNBOND_REASON_REMOTE_DEVICE_DOWN =
            BluetoothDevice.UNBOND_REASON_REMOTE_DEVICE_DOWN;

    /**
     * {@see BluetoothDevice.UNBOND_REASON_DISCOVERY_IN_PROGRESS}
     */
    public static final int UNBOND_REASON_DISCOVERY_IN_PROGRESS =
            BluetoothDevice.UNBOND_REASON_DISCOVERY_IN_PROGRESS;

    /**
     * {@see BluetoothDevice.UNBOND_REASON_AUTH_TIMEOUT}
     */
    public static final int UNBOND_REASON_AUTH_TIMEOUT = BluetoothDevice.UNBOND_REASON_AUTH_TIMEOUT;

    /**
     * {@see BluetoothDevice.UNBOND_REASON_REPEATED_ATTEMPTS}
     */
    public static final int UNBOND_REASON_REPEATED_ATTEMPTS =
            BluetoothDevice.UNBOND_REASON_REPEATED_ATTEMPTS;

    /**
     * {@see BluetoothDevice.UNBOND_REASON_REMOTE_AUTH_CANCELED}
     */
    public static final int UNBOND_REASON_REMOTE_AUTH_CANCELED =
            BluetoothDevice.UNBOND_REASON_REMOTE_AUTH_CANCELED;

    /**
     * {@see BluetoothDevice.UNBOND_REASON_REMOVED}
     */
    public static final int UNBOND_REASON_REMOVED = BluetoothDevice.UNBOND_REASON_REMOVED;

    /**
     * {@see BluetoothDevice.EXTRA_DEVICE}
     */
    public static final String EXTRA_DEVICE = BluetoothDevice.EXTRA_DEVICE;

    public static final String EXTRA_DEVICE_ADDRESS = "android.bluetooth.device.extra.address";
    public static final String EXTRA_PREVIOUS_DEVICE_ADDRESS =
            "android.bluetooth.device.extra.previous_device";
    public static final String EXTRA_DISPLAY_ID = "android.bluetooth.device.extra.displayid";
    public static final String EXTRA_PROFILE = "android.bluetooth.device.extra.profile";

    /**
     * bluetooth node id. {@see BluetoothUtils.BluetoothNode}
     */
    public static final String EXTRA_BLUETOOTH_NODE_ID = "com.mercedes.bluetooth.node.id";

    //APRICOT-524456: Support volume control @{
    /**
     * The remote bluetooth device supports this feature.
     */
    public static final int RESULT_SUCCESS_SUPPORT_FEATURE = 0;

    /**
     * The remote bluetooth device does not support this feature.
     */
    public static final int RESULT_SUCCESS_NOT_SUPPORT_FEATURE = 1;

    /**
     * Get feature failed, corresponding profile not connected.
     * Please ensure that the profile state is connected state.
     */
    public static final int RESULT_FAILURE_PROFILE_NOT_CONNECTED = 2;

    /**
     * Get feature failed, system internal error.
     */
    public static final int RESULT_FAILURE_INTERNAL_ERROR = 4;

    // APRICOT-559275 Indicate that the feature has not been informed by stack yet. @{
    /**
     * The feature has not been informed by stack yet.
     */
    public static final int RESULT_SUCCESS_FEATURE_UNKNOWN = 5;
    // @}

    /**
     * The maximum value of avrcp volume of remote bluetooth device.
     */
    public static final int AVRCP_VOLUME_MAX = 127;

    /**
     * The minimum value of avrcp volume of remote bluetooth device.
     */
    public static final int AVRCP_VOLUME_MIN = 0;

    /**
     * The maximum value of hfp call volume of remote bluetooth device.
     * Note: This includes speaker volume and mic volume.
     */
    public static final int HFP_VOLUME_MAX = 15;

    /**
     * The minimum value of hfp call volume of remote bluetooth device.
     * Note: This includes speaker volume and mic volume.
     */
    public static final int HFP_VOLUME_MIN = 0;

    public static final int HFP_VOLUME_TYPE_SPK = 0;
    public static final int HFP_VOLUME_TYPE_MIC = 1;
    // @} APRICOT-524456
    /**
     * {@see BluetoothDevice.ERROR}
     */
    public static final int ERROR = Integer.MIN_VALUE;

    private final Context mContext;

    private final BluetoothAdapter mBluetoothAdapter;
    private final MBBluetoothProfileManager mProfileManager;
    private final Object mProfileLock = new Object();
    private final BluetoothDevice mBluetoothDevice;
    private short mRssi;
    private final Collection<MBBluetoothProfile> mProfiles = new CopyOnWriteArrayList<>();
    private boolean mJustDiscovered;
    private final Collection<MBBluetoothDeviceCallback> mCallbacks = new CopyOnWriteArrayList<>();

    private final String mAddress;
    private final String mName;

    /**
     * Last time a bt profile auto-connect was attempted.
     * If an ACTION_UUID intent comes in within
     * MAX_UUID_DELAY_FOR_AUTO_CONNECT milliseconds, we will try auto-connect
     * again with the new UUIDs
     */
    private long mConnectAttempted;

    private boolean mIsActiveDeviceA2dp = false;
    private boolean mIsActiveDeviceHeadset = false;
    private final BluetoothNode mNode;
    private int mAssignedDisplayId = INVALID_VALUE;
    private long mTime = INVALID_VALUE;
    private static final int MSG_UUID_UPDATE_TIMEOUT = 1000;
    private static final long MAX_DELAY_TO_TIMEOUT = MAX_UUID_DELAY_FOR_AUTO_CONNECT + 1000;
    private MBBluetoothEventManager mBluetoothEventManager;
    // APRICOT-707857 fix: cannot get timestamp from car after car crashed. @{
    private final MBCarManagerUtils mCarManagerUtils;
    // @}

    /**
     * Get timestamp of this device.
     * @return timestamp of this device
     */
    public long getTimestamp() {
        if (mTime == INVALID_VALUE) {
            logD("getTimestamp: time is invalid, get from car");
            // APRICOT-707857 fix: cannot get timestamp from car after car crashed.
            MBCarBluetoothManager carBluetoothManager = mCarManagerUtils.getCarBluetoothManager();
            if (null != carBluetoothManager) {
                mTime = carBluetoothManager.getTimestamp(this.getAddress());
            }
        }
        logD("getTimestamp: mTime = " + mTime);
        return mTime;
    }

    /**
     * Set timestamp of this device.
     * @param timestamp: timestamp of this device
     */
    void setTimestamp(long timestamp) {
        logI("setTimestamp: timestamp = " + timestamp);
        mTime = timestamp;
    }

    /**
     * Check if the device is assigned to the current display.
     * @param context The context to check the display id
     * @return true if the device is assigned to the current display, false otherwise
     */
    public boolean isAssignedToCurrentDisplay(Context context) {
        boolean ret = getAssignedDisplayId() == context.mbGetDisplayId();
        logI("isAssignedToCurrentDisplay: assignedDisplayId = " + getAssignedDisplayId()
                + ", context.mbGetDisplayId = " + context.mbGetDisplayId());
        return ret;
    }

    /**
     * Get the display id of the current device.
     * @return The display id of the current device.
     */
    public int getAssignedDisplayId() {
        if (mAssignedDisplayId == -1) {
            // APRICOT-268939: Assign Bluetooth device to displayId @{
            // APRICOT-707857 fix: cannot get displayId from car after car crashed.
            MBCarBluetoothManager carBluetoothManager = mCarManagerUtils.getCarBluetoothManager();
            if (null != carBluetoothManager) {
                mAssignedDisplayId = carBluetoothManager.getAssignedDisplay(this.getAddress());
            }
            applyDisplayId(mAssignedDisplayId);
            // @}
        }
        logI("getAssignedDisplayId: mAssignedDisplayId = " + mAssignedDisplayId);
        return mAssignedDisplayId;
    }

    /**
     * Apply the display id of the current device.
     */
    void applyDisplayId(int displayId) {
        this.mAssignedDisplayId = displayId;
        logI("applyDisplayId: displayId = " + displayId);
        ThreadUtils.postOnMainThread(this::dispatchAttributesChanged);
    }

    /**
     * Assign the current device to the {@link android.view.Display} by the given context.
     *
     * @param context: The context which the device should be assigned.
     */
    public void assignToCurrentDisplay(Context context) {
        logD("assignToCurrentDisplay: enter " + mBluetoothDevice);
        int displayId = context.mbGetDisplayId();
        if (mAssignedDisplayId == displayId) {
            logI("assignToCurrentDisplay: already assigned to " + displayId);
            return;
        }
        logI("assignToCurrentDisplay: assignment change: " + mAssignedDisplayId + " --> "
                + displayId);

        assignToDisplay(displayId);
    }

    /**
     * Assign the current device to the {@link android.view.Display} by the given displayId.
     *
     * @param displayId: The display Id which the device should be assigned.
     * @return true for success; false for failed.
     */
    public boolean assignToDisplay(int displayId) {
        logD("assignToDisplay: displayId = " + displayId);
        // APRICOT-707857 fix: cannot set displayId from car after car crashed.
        MBCarBluetoothManager carBluetoothManager = mCarManagerUtils.getCarBluetoothManager();
        boolean ret = false;
        if (carBluetoothManager != null) {
            logI("assignToDisplay: displayId = " + displayId + ", node = " + mNode.toString());
            carBluetoothManager.assignBtDeviceToDisplay(getAddress(), displayId, mNode.ordinal());
            ret = true;
        }

        logI("assignToDisplay: ret = " + ret);

        return ret;
    }

    /**
     * Remove the assignment of the current device to the {@link android.view.Display}.
     */
    public void removeAssignment() {
        logD("removeAssignment");
        assignToDisplay(MBCarBluetoothManager.DISPLAY_NONE);
    }
    // @}

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            LOG.d("handleMessage: msg = " + msg.what);
            if (msg.what == MSG_UUID_UPDATE_TIMEOUT) {
                handleUuidUpdateTimeout();
            } else {
                LOG.w("handleMessage: Connect to profile: " + msg.what + " timeout.");
                refresh();
            }
        }
    };

    /**
     * Handles the timeout for updating the UUID.
     */
    private void handleUuidUpdateTimeout() {
        logI("handleUuidUpdateTimeout");
        // APRICOT-535951 fix: Device Manager is unable to search for beitong game controllers @{
        // unpair this device
        // unpair();
        // @}

        // notify app that uuid update timeout
        if (mBluetoothEventManager != null) {
            mBluetoothEventManager.dispatchUuidUpdateTimeout(this);
        }
    }

    MBBluetoothDevice(Context context, MBBluetoothProfileManager profileManager,
                      BluetoothDevice device) {
        mContext = context;
        mBluetoothAdapter = device.getAdapter();
        mProfileManager = profileManager;
        Optional.ofNullable(mProfileManager).ifPresent(manager ->
                mBluetoothEventManager = manager.getEventManager());
        mBluetoothDevice = device;
        mAddress = device.getAddress();
        mName = device.getAlias();
        int adapterIndex = device.getAdapterIndex();
        LOG.d("MBBluetoothDevice: adapterIndex = " + adapterIndex);
        mNode = (adapterIndex == BluetoothAdapterCommon.ADAPTER_DEFAULT) ? PRIMARY : SECONDARY;
        mCarManagerUtils = MBCarManagerUtils.getInstance(context);
        fillData();
    }

    /**
     * Describes the current device and profile for logging.
     *
     * @param profile Profile to describe
     * @return Description of the device and profile
     */
    private String describe(MBBluetoothProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Address:").append(mBluetoothDevice.getAddress());
        if (profile != null) {
            sb.append(" Profile:").append(profile);
        }
        return sb.toString();
    }

    void onProfileStateChanged(MBBluetoothProfile profile, int newProfileState) {
        mHandler.removeMessages(MSG_UUID_UPDATE_TIMEOUT);
        logI("onProfileStateChanged: profile = " + profile + ", device = "
                + getDeviceDebugInfo(mBluetoothDevice) + ", newProfileState = "
                + getConnectionStateName(newProfileState));

        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF) {
            logI(" BT Turning Off...Profile conn state change ignored...");
            return;
        }

        synchronized (mProfileLock) {
            if (profile instanceof MBA2dpProfile || profile instanceof MBHeadsetProfile) {
                switch (newProfileState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        mHandler.removeMessages(profile.getProfileId());
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        mHandler.sendEmptyMessageDelayed(profile.getProfileId(),
                                MAX_MEDIA_PROFILE_CONNECT_DELAY);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                    case BluetoothProfile.STATE_DISCONNECTED:
                        if (mHandler.hasMessages(profile.getProfileId())) {
                            mHandler.removeMessages(profile.getProfileId());
                        }
                        break;
                    default:
                        LOG.w("onProfileStateChanged(): unknown profile state : "
                                + getConnectionStateName(newProfileState));
                        break;
                }
            }
        }

    }

    /**
     * Disconnect this device.
     */
    public void disconnect() {
        synchronized (mProfileLock) {
            mBluetoothDevice.disconnect();
        }
    }

    /**
     * Disconnect this device from the specified profile.
     *
     * @param profile the profile to use with the remote device
     */
    public void disconnect(MBBluetoothProfile profile) {
        if (profile.setEnabled(this, false)) {
            logI("disconnect: Command sent successfully: DISCONNECT " + describe(profile));
        }
    }

    /**
     * Connect this device.
     */
    public void connect() {
        if (ensurePaired()) {
            return;
        }
        logI("connect()");
        mConnectAttempted = SystemClock.elapsedRealtime();
        connectDevice();
    }

    /**
     * Connect this device to the specified profile.
     */
    private void connectDevice() {
        synchronized (mProfileLock) {
            mHandler.removeMessages(MSG_UUID_UPDATE_TIMEOUT);
            // Try to initialize the profiles if they were not.
            if (mProfiles.isEmpty()) {
                // if mProfiles is empty, then do not invoke updateProfiles. This causes a race
                // condition with carkits during pairing, wherein RemoteDevice.UUIDs have been
                // updated from bluetooth stack but ACTION.uuid is not sent yet.
                // Eventually ACTION.uuid will be received which shall trigger the connection of the
                // various profiles
                // If UUIDs are not available yet, connect will be happen
                // upon arrival of the ACTION_UUID intent.
                logI("No profiles. Maybe we will connect later for device " + mBluetoothDevice);
                mHandler.sendEmptyMessageDelayed(MSG_UUID_UPDATE_TIMEOUT, MAX_DELAY_TO_TIMEOUT);
                return;
            }

            // APRICOT-576051 fix: do NOT connect hid profile for Bluetooth headset @{
            mProfiles.forEach(this::connectProfile);
            // @}
        }
    }

    /**
     * Connect this device to the specified profile.
     *
     * @param profile the profile to use with the remote device
     */
    public void connectProfile(MBBluetoothProfile profile) {
        mConnectAttempted = SystemClock.elapsedRealtime();
        connectInt(profile);
    }

    /**
     * Connect this device to the specified profile.
     *
     * @param profile the profile to use with the remote device
     */
    synchronized void connectInt(MBBluetoothProfile profile) {
        if (ensurePaired()) {
            logI("connectInt: ensurePaired, return.");
            return;
        }

        if (profile.setEnabled(this, true)) {
            logI("connectInt: Command sent successfully: CONNECT " + describe(profile));
            return;
        }

        logI("connectInt: Failed to connect " + profile + " to " + getName());
    }

    /**
     * Check if the device is paired. If not, start pairing.
     *
     * @return true if the device is already paired, false otherwise
     */
    private boolean ensurePaired() {
        int bondState = getBondState();
        logI("ensurePaired: bondState = " + bondState);
        if (bondState == BluetoothDevice.BOND_NONE) {
            return startPairing();
        } else {
            return false;
        }
    }

    /**
     * Start pairing with the remote device for rsu carline.
     *
     * @return true if pairing is started successfully, false otherwise
     */
    public boolean startPairing() {
        int result = startPairingExt();
        logI("startPairing: result = " + result);
        return result == PAIRING_SUCCESS;
    }

    /**
     * Start pairing with the remote device for van carline.
     *
     * @return PAIRING_SUCCESS if pairing is started successfully,
     * PAIRING_ERROR_LIMIT_REACHED if pairing limit is reached,
     * PAIRING_FAILURE otherwise.
     */
    public int startPairingExt() {
        MBBluetoothAdapter bluetoothAdapter = MBBluetoothAdapter.getInstance(mContext);
        // Pairing is unreliable while scanning, so cancel discover
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        if (mBluetoothDevice == null || mBluetoothDevice.getBluetoothClass() == null) {
            logI("startPairingExt: mDevice or bluetoothClass is null, return false.");
            return PAIRING_FAILURE;
        }

        PairingStrategyFactory factory = PairingStrategyFactory.getFactory(CAR_LINE_PROP);
        PairingStrategy pairingStrategy = factory.create(mBluetoothDevice);
        LOG.d("startPairingExt: factory = " + factory.getFactoryName() + ", pairingStrategy = "
                + pairingStrategy.getClass().getSimpleName());
        pairingStrategy.setBluetoothAdapter(bluetoothAdapter);
        int result = pairingStrategy.startPairing(mContext, mBluetoothDevice);
        LOG.i("startPairingExt: result = " + result);
        return result;
    }

    /**
     * Unpair the remote device.
     *
     * @return true if unpairing was successful, false otherwise
     */
    public boolean unpair() {
        int state = mBluetoothDevice.getBondState();
        logD("unpair: state = " + getBondStateName(state));
        if (state == BluetoothDevice.BOND_BONDING) {
            mBluetoothDevice.cancelBondProcess();
        }

        boolean result = mBluetoothDevice.removeBond();
        logI("unpair: remove bond result = " + result);
        return result;
    }

    /**
     * Get the connection state of a profile.
     * @param profile The profile to check
     * @return The connection state of the profile
     */
    public int getProfileConnectionState(MBBluetoothProfile profile) {
        return profile != null ? profile.getConnectionStatus(this)
                : MBBluetoothProfile.STATE_DISCONNECTED;
    }

    /**
     * Fill in the data for this device.
     */
    private void fillData() {
        ParcelUuid[] uuids = mBluetoothDevice.getUuids();
        updateProfiles(uuids);
        dispatchAttributesChanged();
    }

    /**
     * Get the BluetoothDevice object for this device.
     *
     * @return the BluetoothDevice object for this device
     */
    public BluetoothDevice getDevice() {
        return mBluetoothDevice;
    }

    /**
     * Convenience method that can be mocked - it lets tests avoid having to call getDevice() which
     * causes problems in tests since BluetoothDevice is final and cannot be mocked.
     *
     * @return the address of this device
     */
    public String getAddress() {
        return mBluetoothDevice.getAddress();
    }

    /**
     * Get identity address from remote device
     *
     * @return {@link BluetoothDevice#getIdentityAddress()} if
     * {@link BluetoothDevice#getIdentityAddress()} is not null otherwise return
     * {@link BluetoothDevice#getAddress()}
     */
    public String getIdentityAddress() {
        final String identityAddress = mBluetoothDevice.getIdentityAddress();
        return TextUtils.isEmpty(identityAddress) ? getAddress() : identityAddress;
    }

    /**
     * Get name from remote device
     *
     * @return {@link BluetoothDevice#getAlias()} if
     * {@link BluetoothDevice#getAlias()} is not null otherwise return null
     */
    public String getName() {
        return mBluetoothDevice.getAlias();
    }

    /**
     * User changes the device name
     *
     * @param name new alias name to be set, should never be null
     */
    public void setName(String name) {
        // Prevent getName() to be set to null if setName(null) is called
        if (name == null || TextUtils.equals(name, getName())) {
            logI("setName: name is null or same as current name, return.");
            return;
        }
        mBluetoothDevice.setAlias(name);
        dispatchAttributesChanged();
    }

    /**
     * Set the active state of the device for a specific profile.
     *
     * @param profile  The profile to set the active state for
     * @param enabled  True to set the device as active, false to set it as inactive
     * @return true if the operation was successful, false otherwise
     */
    public boolean setActive(int profile, boolean enabled) {
        logI("setActive: displayId = " + mAssignedDisplayId + ", profile = " + profile);
        MBCarBluetoothManager carBluetoothManager = mCarManagerUtils.getCarBluetoothManager();
        if (carBluetoothManager == null) {
            LOG.e("setActive failed! carBluetoothManager is null.");
            return false;
        }
        return carBluetoothManager.setActive(this.getAddress(), mNode.ordinal(), mAssignedDisplayId,
                profile, enabled);
    }

    /**
     * Get battery level from remote device
     *
     * @return battery level in percentage [0-100],
     * {@link BluetoothDevice#BATTERY_LEVEL_BLUETOOTH_OFF}, or
     * {@link BluetoothDevice#BATTERY_LEVEL_UNKNOWN}
     */
    public int getBatteryLevel() {
        return mBluetoothDevice.getBatteryLevel();
    }

    // APRICOT-274970: add charging identifier of bluetooth device @{

    /**
     * Get battery charging state from remote device
     *
     * @return True if remote device is charging, otherwise return false.
     */
    public boolean isBatteryCharging() {
        return mBluetoothDevice.isBatteryCharging();
    }
    // @}

    /**
     * Refresh the device information.
     */
    public void refresh() {
        logD("refresh()");
        dispatchAttributesChanged();
    }

    /**
     * Set the just discovered state of the device.
     *
     * @param justDiscovered true if the device was just discovered, false otherwise
     */
    public void setJustDiscovered(boolean justDiscovered) {
        if (mJustDiscovered != justDiscovered) {
            mJustDiscovered = justDiscovered;
            dispatchAttributesChanged();
        }
    }

    /**
     * Get the bond state of the remote device.
     * <p>Possible values for the bond state are:
     * {@link #BOND_NONE},
     * {@link #BOND_BONDING},
     * {@link #BOND_BONDED}.
     *
     * @return the bond state
     */
    public int getBondState() {
        return mBluetoothDevice.getBondState();
    }

    /**
     * Update the device status as active or non-active per Bluetooth profile.
     *
     * @param isActive         true if the device is active
     * @param bluetoothProfile the Bluetooth profile
     */
    public void onActiveDeviceChanged(boolean isActive, int bluetoothProfile) {
        boolean changed = false;
        switch (bluetoothProfile) {
            case BluetoothProfile.A2DP:
                changed = (mIsActiveDeviceA2dp != isActive);
                mIsActiveDeviceA2dp = isActive;
                break;
            case BluetoothProfile.HEADSET:
                changed = (mIsActiveDeviceHeadset != isActive);
                mIsActiveDeviceHeadset = isActive;
                break;
            default:
                LOG.w("onActiveDeviceChanged: unknown profile = " + bluetoothProfile +
                        ", isActive = " + isActive);
                break;
        }
        if (changed) {
            dispatchAttributesChanged();
        }
    }

    /**
     * Check if the device is active for a specific profile.
     *
     * @param profileId The profile ID to check against
     * @return true if the device is active for the specified profile, false otherwise
     */
    public boolean isActive(int profileId) {
        MBBluetoothAdapter bluetoothAdapter = MBBluetoothAdapter.getInstance(mContext);
        List<MBBluetoothDevice> activeDevices = Optional.ofNullable(bluetoothAdapter)
                .map(adapter -> adapter.getActiveDevices(profileId, mAssignedDisplayId))
                .orElseGet(Collections::emptyList);

        if (activeDevices.isEmpty()) {
            logI("isActive: activeDevices is null or empty, return false.");
            return false;
        }

        boolean ret = this.equals(activeDevices.get(0));
        logI("isActive ? " + ret);
        return ret;
    }

    /**
     * Update the profile audio state.
     */
    void onAudioModeChanged() {
        dispatchAttributesChanged();
    }

    /**
     * Set the RSSI value of the remote device.
     *
     * @param rssi the RSSI value
     */
    void setRssi(short rssi) {
        if (mRssi != rssi) {
            mRssi = rssi;
            dispatchAttributesChanged();
        }
    }

    /**
     * Get the RSSI value of the remote device.
     *
     * @return the RSSI value
     */
    public short getRssi() {
        return this.mRssi;
    }

    /**
     * Checks whether we are connected to this device (all profile is connected).
     *
     * @return Whether it is connected.
     */
    public boolean isConnected() {
        if (mProfiles.isEmpty()) {
            logI("isConnected: mProfiles is empty, return false.");
            return false;
        }
        boolean isConnected = isDeviceInState(BluetoothProfile.STATE_CONNECTED);
        logI("isConnected: isConnected = " + isConnected);
        return isConnected;
    }

    /**
     * Checks whether we are disconnected to this device (all profile are disconnected).
     *
     * @return Whether it is disconnected.
     */
    public boolean isDisconnected() {
        if (mProfiles.isEmpty()) {
            logI("isDisconnected: mProfiles is empty, return true.");
            return true;
        }
        return isDeviceInState(BluetoothProfile.STATE_DISCONNECTED);
    }

    /**
     * Checks whether we are connecting to this device (any profile is connecting).
     *
     * @return Whether it is connecting.
     */
    public boolean isConnecting() {
        synchronized (mProfileLock) {
            return mProfiles.stream().anyMatch(profile -> getProfileConnectionState(profile)
                    == BluetoothProfile.STATE_CONNECTING);
        }
    }

    /**
     * Checks whether we are disconnecting to this device (any profile is disconnecting).
     *
     * @return Whether it is disconnecting.
     */
    public boolean isDisconnecting() {
        synchronized (mProfileLock) {
            return mProfiles.stream().anyMatch(profile -> getProfileConnectionState(profile)
                    == BluetoothProfile.STATE_DISCONNECTING);
        }
    }

    /**
     * Checks whether the device is in a specified state.
     *
     * @param state The target state to check against.
     * @return true if all Bluetooth profiles of the device are in the specified state;
     * otherwise, false.
     */
    public boolean isDeviceInState(int state) {
        logI("isDeviceInState: target state = " + state);

        if (mProfiles.isEmpty()) {
            return false;
        }

        synchronized (mProfileLock) {
            for (MBBluetoothProfile profile : mProfiles) {
                int status = getProfileConnectionState(profile);
                if (status != state) {
                    logI("isDeviceInState: profile " + profile.getProfileId() + " status is "
                            + status);
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * Checks whether the device is connected to a specific profile.
     *
     * @param profile The target profile to check against.
     * @return true if the device is connected to the specified profile;
     * otherwise, false.
     */
    public boolean isConnectedProfile(MBBluetoothProfile profile) {
        int status = getProfileConnectionState(profile);
        return status == BluetoothProfile.STATE_CONNECTED;
    }

    /**
     * Checks whether the device is busy (connecting or disconnecting).
     *
     * @return true if the device is busy; otherwise, false.
     */
    public boolean isBusy() {
        return isBusyState(this);
    }

    /**
     * Checks whether the device is busy (connecting or disconnecting).
     *
     * @param device The target device to check against.
     * @return true if the device is busy; otherwise, false.
     */
    private boolean isBusyState(MBBluetoothDevice device) {
        for (MBBluetoothProfile profile : device.getProfiles()) {
            int status = device.getProfileConnectionState(profile);
            if (status == BluetoothProfile.STATE_CONNECTING
                    || status == BluetoothProfile.STATE_DISCONNECTING) {
                return true;
            }
        }
        return device.getBondState() == BluetoothDevice.BOND_BONDING;
    }

    /**
     * Updates the profiles of the device.
     *
     * @param uuids the UUIDs of the device
     */
    private void updateProfiles(ParcelUuid[] uuids) {
        if (uuids == null || uuids.length == 0) {
            logI("updateProfiles: uuids is null, return.");
            return;
        }

        List<ParcelUuid> uuidsList = mBluetoothAdapter.getUuidsList();
        if (uuidsList.isEmpty()) {
            logI("updateProfiles: uuidsList is null, return.");
            return;
        }

        ParcelUuid[] localUuids = new ParcelUuid[uuidsList.size()];
        uuidsList.toArray(localUuids);

        synchronized (mProfileLock) {
            mProfileManager.updateProfiles(uuids, localUuids, mProfiles);
            // APRICOT-576051 fix: do NOT connect hid profile for Bluetooth headset @{
            MBBluetoothClass bluetoothClass = getBluetoothClass();
            int majorClass = Optional.ofNullable(bluetoothClass)
                    .map(MBBluetoothClass::getMajorDeviceClass)
                    .orElse(BluetoothClass.Device.Major.UNCATEGORIZED);
            logD("updateProfiles: majorClass = " + majorClass);
            if (majorClass == AUDIO_VIDEO) {
                logD("updateProfiles: remove HID profile for audio video device.");
                mProfiles.stream().filter(
                        profile -> profile.getProfileId() == MBBluetoothProfile.HID_HOST).forEach(
                        this::disconnect);
                mProfiles.removeIf(profile -> profile.getProfileId() == MBBluetoothProfile.HID_HOST);
            }
            // @}
        }

        // just log @{
        logI("updateProfiles: mProfiles.size = " + mProfiles.size());

        mProfiles.forEach(
                mbBluetoothProfile -> logI("updateProfiles: profile = " + mbBluetoothProfile));

        logD("updateProfiles: updating profiles for " + getDeviceDebugInfo(mBluetoothDevice));
        MBBluetoothClass bluetoothClass = getBluetoothClass();
        if (bluetoothClass != null) {
            LOG.v("updateProfiles: Class = " + bluetoothClass);
        }

        logD("UUID:");
        for (ParcelUuid uuid : uuids) {
            LOG.v("  " + uuid);
        }
        // @}
    }

    /**
     * Refreshes the UI when framework alerts us of a UUID change.
     *
     * @param uuids the UUIDs of the device
     */
    void onUuidChanged(ParcelUuid[] uuids) {
        if (uuids == null || uuids.length == 0) {
            logI("onUuidChanged: uuids is null, return;");
            return ;
        }
        updateProfiles(uuids);

        long timeout = MAX_UUID_DELAY_FOR_AUTO_CONNECT;
        if (ArrayUtils.contains(uuids, BluetoothUuid.HOGP)) {
            timeout = MAX_HOGP_DELAY_FOR_AUTO_CONNECT;
        }

        logI("onUuidChanged: Time since last connect="
                + (SystemClock.elapsedRealtime() - mConnectAttempted));

        /*
         * If a connect was attempted earlier without any UUID, we will do the connect now.
         * Otherwise, allow the connect on UUID change.
         */
        if ((mConnectAttempted + timeout) > SystemClock.elapsedRealtime()) {
            logI("onUuidChanged: triggering connectDevice");
            connectDevice();
        }

        dispatchAttributesChanged();
    }

    /**
     * Called when the bonding state of the device changes.
     * @param bondState The new bond state
     */
    void onBondingStateChanged(int bondState) {
        logD("onBondingStateChanged: bondState = " + getBondStateName(bondState));
        if (bondState == BluetoothDevice.BOND_NONE) {
            synchronized (mProfileLock) {
                mProfiles.clear();
            }
            // APRICOT-403296 : reset the assigned display id and timestamp when bond_node @{
            mAssignedDisplayId = INVALID_VALUE;
            mTime = INVALID_VALUE;
            // @}
        }

        refresh();
    }

    /**
     * Get the Bluetooth class of the remote device.
     *
     * @return Bluetooth class object, or null on error
     */
    public MBBluetoothClass getBluetoothClass() {
        BluetoothClass bluetoothClass = mBluetoothDevice.getBluetoothClass();
        if (bluetoothClass == null) {
            return null;
        }
        return new MBBluetoothClass(bluetoothClass);
    }

    /**
     * Get the Bluetooth class of the remote device.
     *
     * @return Bluetooth class object, or null on error
     */
    public List<MBBluetoothProfile> getProfiles() {
        return new ArrayList<>(mProfiles);
    }

    /**
     * Get the connectable profiles of the remote device.
     *
     * @return List of connectable profiles
     */
    public List<MBBluetoothProfile> getConnectableProfiles() {
        List<MBBluetoothProfile> connectableProfiles = new ArrayList<>();
        synchronized (mProfileLock) {
            for (MBBluetoothProfile profile : mProfiles) {
                if (profile.accessProfileEnabled()) {
                    connectableProfiles.add(profile);
                }
            }
        }
        return connectableProfiles;
    }

    /**
     * Register callback.
     *
     * @param callback the callback
     */
    public void registerCallback(MBBluetoothDeviceCallback callback) {
        mCallbacks.add(callback);
    }

    /**
     * Unregister callback.
     *
     * @param callback the callback
     */
    public void unregisterCallback(MBBluetoothDeviceCallback callback) {
        mCallbacks.remove(callback);
    }

    /**
     * Dispatch the attributes changed event to all registered callbacks.
     */
    void dispatchAttributesChanged() {
        for (MBBluetoothDeviceCallback callback : mCallbacks) {
            callback.onDeviceAttributesChanged();
        }
    }

    @Override
    public String toString() {
        return mBluetoothDevice.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MBBluetoothDevice || o instanceof BluetoothDevice) {
            return (o instanceof MBBluetoothDevice) && mBluetoothDevice.equals(
                    ((MBBluetoothDevice) o).getDevice());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return mBluetoothDevice.getAddress().hashCode();
    }

    /**
     * This comparison uses non-final fields so the sort order may change when device attributes
     * change (such as bonding state).
     * Settings will completely refresh the device list when this happens.
     *
     * @param another the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than
     */
    @Override
    public int compareTo(MBBluetoothDevice another) {
        // Connected above not connected
        int comparison = (another.isConnected() ? 1 : 0) - (isConnected() ? 1 : 0);
        if (comparison != 0) {
            return comparison;
        }

        // Paired above not paired
        comparison = (another.getBondState() == BluetoothDevice.BOND_BONDED ? 1 : 0) -
                (getBondState() == BluetoothDevice.BOND_BONDED ? 1 : 0);
        if (comparison != 0) {
            return comparison;
        }

        // Just discovered above discovered in the past
        if (another.getTimestamp() > getTimestamp()) {
            return 1;
        } else if (another.getTimestamp() < getTimestamp()) {
            return -1;
        }

        // Stronger signal above weaker signal
        comparison = another.mRssi - mRssi;
        if (comparison != 0) {
            return comparison;
        }

        // Fallback on name
        if (null == getName()) {
            return comparison;
        }
        return getName().compareTo(another.getName());
    }

    /**
     * The interface Mb bluetooth device callback.
     */
    public interface MBBluetoothDeviceCallback {
        /**
         * Method triggered when the attributes of a device have changed.
         */
        void onDeviceAttributesChanged();
    }

    /**
     * @return {@code true} if {@code MBBluetoothDevice} is a2dp device
     */
    public boolean isConnectedA2dpDevice() {
        MBA2dpProfile a2dpProfile = mProfileManager.getA2dpProfile();
        return a2dpProfile != null && a2dpProfile.getConnectionStatus(this) ==
                BluetoothProfile.STATE_CONNECTED;
    }

    /**
     * @return {@code true} if {@code MBBluetoothDevice} is HFP device
     */
    public boolean isConnectedHfpDevice() {
        MBHeadsetProfile headsetProfile = mProfileManager.getHeadsetProfile();
        return headsetProfile != null && headsetProfile.getConnectionStatus(this) ==
                BluetoothProfile.STATE_CONNECTED;
    }

    /**
     * @return {@code true} if {@code MBBluetoothDevice} is HID Host device
     */
    public boolean isConnectedHidHostDevice() {
        MBHidHostProfile hidProfile = mProfileManager.getHidHostProfile();
        return hidProfile != null && hidProfile.getConnectionStatus(this) ==
                BluetoothProfile.STATE_CONNECTED;
    }

    /**
     * Get the Bluetooth node.
     * {@link BluetoothNode#PRIMARY} for the AOSP bluetooth node, and
     * {@link BluetoothNode#SECONDARY} for the extension bluetooth node.
     *
     * @return bluetooth node
     */
    public BluetoothNode getBluetoothNode() {
        logD("getBluetoothNode: return " + mNode.toString());
        return mNode;
    }

    /**
     * Get connection status of this device.
     *
     * @return connection status.
     */
    public int getConnectionStatus() {
        synchronized (mProfileLock) {
            boolean allConnected = true;

            for (MBBluetoothProfile profile : getProfiles()) {
                int connectionState = getProfileConnectionState(profile);
                logI("getConnectionStatus: profile = " + profile + ", connectionState = "
                        + getConnectionStateName(connectionState));

                switch (connectionState) {
                    case BluetoothProfile.STATE_CONNECTING:
                        return BluetoothProfile.STATE_CONNECTING;

                    case BluetoothProfile.STATE_DISCONNECTING:
                        return BluetoothProfile.STATE_DISCONNECTING;

                    case BluetoothProfile.STATE_DISCONNECTED:
                        allConnected = false;
                        break;

                    case BluetoothProfile.STATE_CONNECTED:
                    default:
                        break;
                }
            }

            if (allConnected) {
                return MBBluetoothProfile.STATE_CONNECTED;
            }
        }

        return MBBluetoothProfile.STATE_DISCONNECTED;
    }

    /**
     * Get UUIDs of this device.
     * @return UUIDs of this device
     */
    public ParcelUuid[] getUuids() {
        return mBluetoothDevice.getUuids();
    }

    /**
     * Gets whether bonding was initiated locally
     *
     * @return true if bonding is initiated locally, false otherwise
     */
    public boolean isBondingInitiatedLocally() {
        boolean ret = mBluetoothDevice.isBondingInitiatedLocally();
        LOG.d("isBondingInitiatedLocally: ret = " + ret);
        return ret;
    }

    /**
     * Confirm passkey for pairing.
     **/
    public void setPairingConfirmation(boolean confirm) {
        LOG.i("setPairingConfirmation: confirm = " + confirm);
        mBluetoothDevice.setPairingConfirmation(confirm);
    }

    // APRICOT-485320 fix: provide API for car to set displayId into BluetoothService @{

    /**
     * Only for CarService to update displayId to Bluetooth Service
     *
     * @param displayId: display identifier
     */
    public void setDisplayToService(int displayId) {
        logI("setDisplayToService: displayId = " + displayId);
        mBluetoothDevice.setDisplayId(displayId);
    }
    // @} APRICOT-485320 fix

    /**
     * Start the bonding (pairing) process with the remote device.
     *
     * @return false on immediate error, true if bonding will begin
     */
    public boolean createBond() {
        return mBluetoothDevice.createBond();
    }

    /**
     * Create an RFCOMM BluetoothSocket ready to start a secure outgoing connection
     * to this remote device using SDP lookup of uuid.
     *
     * @param uuid service record uuid to lookup RFCOMM channel
     * @return a RFCOMM BluetoothServerSocket ready for an outgoing connection
     * @throws IOException on error, for example Bluetooth not available, or insufficient
     * permissions
     */
    public MBBluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        LOG.i("createRfcommSocketToServiceRecord");
        return new MBBluetoothSocket(mBluetoothDevice.createRfcommSocketToServiceRecord(uuid));
    }

    /**
     * Create an RFCOMM BluetoothSocket socket ready to start an insecure outgoing
     * connection to this remote device using SDP lookup of uuid.
     *
     * @param uuid service record uuid to lookup RFCOMM channel
     * @return a RFCOMM BluetoothServerSocket ready for an outgoing connection
     * @throws IOException on error, for example Bluetooth not available, or insufficient
     * permissions
     */
    public MBBluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        LOG.i("createRfcommSocketToServiceRecord");
        return new MBBluetoothSocket(mBluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid));
    }

    //APRICOT-524456: Support volume control @{

    /**
     * Check whether the remote device support hfp volume control feature.
     *
     * @return state for remote device supports hfp volume control
     * otherwise return false.
     */
    @SupportFeatureResult
    public int isHfpVolumeControlSupported() {
        if (mBluetoothDevice == null) {
            LOG.e("isHfpVolumeControlSupported failed, device is null");
            return RESULT_FAILURE_INTERNAL_ERROR;
        }
        if (mProfileManager == null) {
            LOG.e("isHfpVolumeControlSupported failed, profile manager is null");
            return RESULT_FAILURE_INTERNAL_ERROR;
        }
        MBHeadsetProfile profile = mProfileManager.getHeadsetProfile();
        if (profile == null) {
            LOG.e("isHfpVolumeControlSupported failed, headset profile is null");
            return RESULT_FAILURE_INTERNAL_ERROR;
        }
        if (profile.getConnectionStatus(this) != MBBluetoothProfile.STATE_CONNECTED) {
            LOG.e("isHfpVolumeControlSupported failed, headset profile is not connected");
            return RESULT_FAILURE_PROFILE_NOT_CONNECTED;
        }

        // APRICOT-559275 get from bluetooth service @{
        int supportState = mBluetoothDevice.isHfpVolumeControlSupported();

        int ret = getSupportedCode(supportState);
        LOG.i("isSupportAvrcpAbsVolumeControl, ret = " + ret + ", supportState = " + supportState);
        return ret;
        // APRICOT-559275 @}
    }

    // APRICOT-559275 Change code to lib code @{
    /**
     * Retrieves the supported encoding based on the given support state.
     *
     * @param supportState The support state, which determines the returned encoding.
     * @return The encoding corresponding to the support state.
     */
    private static int getSupportedCode(int supportState) {
        int ret = RESULT_FAILURE_INTERNAL_ERROR;
        switch (supportState) {
            case BluetoothDevice.VOLUME_CONTROL_SUPPORTED:
            case BluetoothDevice.VOLUME_CONTROL_UNKNOWN:
                ret = RESULT_SUCCESS_SUPPORT_FEATURE;
                break;
            case BluetoothDevice.VOLUME_CONTROL_NOT_SUPPORT:
                ret = RESULT_SUCCESS_NOT_SUPPORT_FEATURE;
                break;
            default:
                ret = RESULT_SUCCESS_FEATURE_UNKNOWN;
                LOG.w("The supportState is invalid! " + supportState);
                break;
        }
        return ret;
    }
    // APRICOT-559275 @}

    /**
     * Check whether the remote device support avrcp absolute volume control feature.
     *
     * @return state for remote device supports avrcp absolute volume control
     * otherwise return false.
     */
    @SupportFeatureResult
    public int isAvrcpAbsVolumeControlSupported() {
        if (mBluetoothDevice == null) {
            LOG.e("isAvrcpAbsVolumeControlSupported failed, device is null");
            return RESULT_FAILURE_INTERNAL_ERROR;
        }
        if (mProfileManager == null) {
            LOG.e("isAvrcpAbsVolumeControlSupported failed, profile manager is null");
            return RESULT_FAILURE_INTERNAL_ERROR;
        }
        MBA2dpProfile profile = mProfileManager.getA2dpProfile();
        if (profile == null) {
            LOG.e("isAvrcpAbsVolumeControlSupported failed, a2dp profile is null");
            return RESULT_FAILURE_INTERNAL_ERROR;
        }
        if (profile.getConnectionStatus(this) != MBBluetoothProfile.STATE_CONNECTED) {
            LOG.e("isAvrcpAbsVolumeControlSupported failed, a2dp profile is not connected");
            return RESULT_FAILURE_PROFILE_NOT_CONNECTED;
        }
        // APRICOT-559275 Get the state of feature @{
        int supportState = mBluetoothDevice.isAvrcpAbsVolumeControlSupported();
        int ret = getSupportedCode(supportState);
        LOG.i("isSupportAvrcpAbsVolumeControl: ret = " + ret + ", supportState = " + supportState);
        return ret;
    }

    /**
     * Set the absolute volume of the remote device.
     *
     * @param volume The volume level to set (0-100)
     * @return true if the operation was successful, false otherwise
     */
    public boolean setAvrcpAbsVolume(int volume) {
        LOG.i("setAvrcpAbsVolume for " + getName() + ",volume:" + volume);
        MBA2dpProfile a2dpProfile = mProfileManager.getA2dpProfile();
        if (a2dpProfile == null) {
            LOG.e("setAvrcpAbsVolume failed! a2dpProfile is null");
            return false;
        }
        if (MBBluetoothProfile.STATE_CONNECTED != a2dpProfile.getConnectionStatus(this)) {
            LOG.e("setAvrcpAbsVolume failed! a2dp has not connected");
            return false;
        }
        if (BluetoothDevice.VOLUME_CONTROL_NOT_SUPPORT == mBluetoothDevice.isAvrcpAbsVolumeControlSupported()) {
            LOG.e("setAvrcpAbsVolume failed! device does not support avrcp abs volume control.");
            return false;
        }
        a2dpProfile.setAvrcpAbsoluteVolumeOfDevice(this, volume);
        return true;
    }

    //APRICOT-923202: fix Bluetooth headset not remember the last volume when restart the vehicle @{
    /**
     * Set the absolute volume of the remote device.
     * When user click mute button,set the volume and mute state to BT FW.
     *
     * @param volume The volume level to set (0-100)
     * @param isMute The mute state (true/false)
     * @return true if the operation was successful, false otherwise
     */
    public boolean setAvrcpAbsVolume(int volume, boolean isMute) {
        LOG.i("setAvrcpAbsVolume for " + getName() + ",volume:" + volume + ",isMute:" + isMute);
        MBA2dpProfile a2dpProfile = mProfileManager.getA2dpProfile();
        if (a2dpProfile == null) {
            LOG.e("setAvrcpAbsVolume failed! a2dpProfile is null");
            return false;
        }
        if (MBBluetoothProfile.STATE_CONNECTED != a2dpProfile.getConnectionStatus(this)) {
            LOG.e("setAvrcpAbsVolume failed! a2dp has not connected");
            return false;
        }
        if (BluetoothDevice.VOLUME_CONTROL_NOT_SUPPORT == mBluetoothDevice.isAvrcpAbsVolumeControlSupported()) {
            LOG.e("setAvrcpAbsVolume failed! device does not support avrcp abs volume control.");
            return false;
        }
        a2dpProfile.setAvrcpAbsoluteVolumeOfDevice(this, volume, isMute);
        return true;
    }
    // @} APRICOT-923202

    /**
     * Get the absolute volume of the remote device.
     *
     * @return The absolute volume level (0-100) or INVALID_VALUE if not supported
     */
    public int getAvrcpAbsoluteVolume() {
        MBA2dpProfile a2dpProfile = mProfileManager.getA2dpProfile();
        if (a2dpProfile == null) {
            LOG.e("getAvrcpAbsoluteVolume failed! a2dpProfile is null");
            return INVALID_VALUE;
        }
        if (MBBluetoothProfile.STATE_CONNECTED != a2dpProfile.getConnectionStatus(this)) {
            LOG.e("getAvrcpAbsoluteVolume failed! a2dp has not connected");
            return INVALID_VALUE;
        }
        if (BluetoothDevice.VOLUME_CONTROL_NOT_SUPPORT == mBluetoothDevice.isAvrcpAbsVolumeControlSupported()) {
            LOG.e("getAvrcpAbsoluteVolume failed! device does not support avrcp abs volume " +
                    "control.");
            return INVALID_VALUE;
        }
        int volume = a2dpProfile.getAvrcpAbsoluteVolumeOfDevice(this);
        LOG.i("getAvrcpAbsoluteVolume: avrcp abs volume " + volume);
        return volume;
    }

    /**
     * Set the call volume of the remote device.
     *
     * @param volume The volume level to set (0-100)
     * @param type   The type of volume to set (e.g., call, media)
     * @return true if the operation was successful, false otherwise
     */
    public boolean setCallVolume(int volume, int type) {
        LOG.i("setCallVolume: volume = " + volume + ", type = " + type);
        MBHeadsetProfile profile = mProfileManager.getHeadsetProfile();
        if (profile == null) {
            LOG.e("setCallVolume failed! headset profile is null");
            return false;
        }
        if (MBBluetoothProfile.STATE_CONNECTED != profile.getConnectionStatus(this)) {
            LOG.e("setCallVolume failed! headset profile has not connected");
            return false;
        }
        return profile.setCallVolume(this, volume, type);
    }

    /**
     * Set the call volume of the remote device with mute state.
     * When user click mute button,set the volume and mute state to BT FW.
     *
     * @param volume The volume level to set (0-100)
     * @param type   The type of volume to set (e.g., call, media)
     * @param isMuted Whether the current volume state is muted
     * @return true if the operation was successful, false otherwise
     */
    public boolean setCallVolume(int volume, int type, boolean isMuted) {
        LOG.i("setCallVolume: volume = " + volume + ", type = " + type + ", isMuted = " + isMuted);
        MBHeadsetProfile profile = mProfileManager.getHeadsetProfile();
        if (profile == null) {
            LOG.e("setCallVolume failed! headset profile is null");
            return false;
        }
        if (MBBluetoothProfile.STATE_CONNECTED != profile.getConnectionStatus(this)) {
            LOG.e("setCallVolume failed! headset profile has not connected");
            return false;
        }
        return profile.setCallVolume(this, volume, type, isMuted);
    }

    @IntDef({
            RESULT_SUCCESS_SUPPORT_FEATURE,
            RESULT_SUCCESS_NOT_SUPPORT_FEATURE,
            RESULT_FAILURE_PROFILE_NOT_CONNECTED,
            RESULT_FAILURE_INTERNAL_ERROR,
            RESULT_SUCCESS_FEATURE_UNKNOWN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SupportFeatureResult {
    }

    /**
     * Get the audio state of the remote device.
     *
     * @return The audio state of the remote device
     */
    public int getAudioState() {
        int ret = -1;
        MBHeadsetProfile profile = mProfileManager.getHeadsetProfile();
        if (profile == null) {
            LOG.e("getAudioState failed! headset profile is null");
            return ret;
        }
        if (MBBluetoothProfile.STATE_CONNECTED != profile.getConnectionStatus(this)) {
            LOG.e("getAudioState failed! headset profile has not connected");
            return ret;
        }
        return profile.getAudioState(this);
    }

    /**
     * Check whether the audio state of the remote device is on.
     * Note: When the SCO audio state is in disconnecting state, it is considered as off.
     *
     * @return true if the audio state is on, false otherwise
     */
    public boolean isAudioStateOn() {
        LOG.d("isAudioStateOn");
        boolean ret = false;
        MBHeadsetProfile profile = mProfileManager.getHeadsetProfile();
        if (profile == null) {
            LOG.e("isAudioStateOn failed! headset profile is null");
            return ret;
        }
        if (MBBluetoothProfile.STATE_CONNECTED != profile.getConnectionStatus(this)) {
            LOG.e("isAudioStateOn failed! headset profile has not connected");
            return ret;
        }
        return profile.isAudioStateOn(this);
    }

    /**
     * Get the user ID of the remote device.
     *
     * @return The user ID of the remote device
     */
    public int getUserId() {
        return mBluetoothDevice.getUserId();
    }

    // @} APRICOT-524456

    /**
     * Determines if the device is a Remote Controller.
     *
     * @return true if the device is a Remote Controller; false otherwise.
     */
    public boolean isRemoteController() {
        return mBluetoothDevice.isRemoteController();
    }

    /**
     * Determines whether the device is a headset.
     *
     * @return true if the device is identified as a headset, false otherwise.
     */
    public boolean isHeadset() {
        MBBluetoothClass bluetoothClass = getBluetoothClass();
        int majorDeviceClass = Optional.ofNullable(bluetoothClass)
                .map(MBBluetoothClass::getMajorDeviceClass)
                .orElse(UNCATEGORIZED);

        LOG.i("isHeadset: majorDeviceClass = 0x" + Integer.toHexString(majorDeviceClass));
        return majorDeviceClass == AUDIO_VIDEO;
    }

    /**
     * Determines whether the device is a GameController.
     *
     * @return true if the device is identified as a GameController, false otherwise.
     */
    public boolean isGamepad() {
        MBBluetoothClass bluetoothClass = getBluetoothClass();
        int deviceClass = Optional.ofNullable(bluetoothClass)
                .map(MBBluetoothClass::getDeviceClass)
                .orElse(INVALID_CLASS);
        int majorDeviceClass = Optional.ofNullable(bluetoothClass)
                .map(MBBluetoothClass::getMajorDeviceClass)
                .orElse(UNCATEGORIZED);

        LOG.i("isGamepad: deviceClass = 0x" + Integer.toHexString(deviceClass)
                + ", majorDeviceClass = 0x" + Integer.toHexString(majorDeviceClass));
        boolean isPeripheralGamepad =
                deviceClass == PERIPHERAL_GAME_CONTROLLER || deviceClass == PERIPHERAL_JOY_STICK;
        return majorDeviceClass == PERIPHERAL && isPeripheralGamepad;
    }

    /**
     * Determine whether the connected devices for each bluetooth chip has reached its maximum value
     *
     * @return true if the connected device is reached its maximum value, false otherwise.
     */
    public boolean isReachedLimitation() {
        if (RSU_CAR_LINE.equals(CAR_LINE_PROP)) {
            LOG.i("isReachedLimitation: rsu car line, return false");
            return false;
        }

        int type = getDeviceType();
        if (type == TYPE_INVALID) {
            LOG.i("isReachedLimitation: type is invalid");
            return false;
        }
        int num = getConnectedDeviceNumber(type);
        LOG.i("isReachedLimitation: connectedDeviceNumber = " + num);
        return num >= MAXIMUM_CONNECTED_NUMBER;
    }

    /**
     * Get the number of connected devices
     *
     * @return the number of connected devices.
     */
    private int getConnectedDeviceNumber(int type) {
        MBBluetoothAdapter bluetoothAdapter = MBBluetoothAdapter.getInstance(mContext);
        if (bluetoothAdapter == null) {
            LOG.i("getConnectedDeviceNumber: mBluetoothAdapter is null");
            return DEFAULT_CONNECTED_NUMBER;
        }
        return (int) bluetoothAdapter.getBondedDevices()
                .stream()
                .filter(device -> device.getBluetoothNode().equals(mNode) && device.isConnected())
                .filter(type == TYPE_HEADSET ? MBBluetoothDevice::isHeadset :
                        MBBluetoothDevice::isGamepad)
                .count();
    }

    /**
     * Get the type of the bluetooth device
     *
     * @return the type of  device.
     */
    private int getDeviceType() {
        MBBluetoothClass bluetoothClass = getBluetoothClass();
        int deviceClass = Optional.ofNullable(bluetoothClass)
                .map(MBBluetoothClass::getDeviceClass)
                .orElse(INVALID_CLASS);
        int majorDeviceClass = Optional.ofNullable(bluetoothClass)
                .map(MBBluetoothClass::getMajorDeviceClass)
                .orElse(UNCATEGORIZED);
        LOG.i("getDeviceType: deviceClass = 0x" + Integer.toHexString(deviceClass)
                + ", majorDeviceClass = 0x" + Integer.toHexString(majorDeviceClass));

        boolean isPeripheralGamepad =
                deviceClass == PERIPHERAL_GAME_CONTROLLER || deviceClass == PERIPHERAL_JOY_STICK;

        if (majorDeviceClass == AUDIO_VIDEO) {
            return TYPE_HEADSET;
        } else if (majorDeviceClass == PERIPHERAL && isPeripheralGamepad) {
            return TYPE_GAMECONTROLLER;
        }
        return TYPE_INVALID;
    }

    /**
     * APRICOT-784827: Check whether SCMS-T of the specified device is enabled.
     *
     * @return true if SCMS-T of the specified device is enabled, false otherwise
     */
    public boolean isScmstEnabled() {
        MBA2dpProfile a2dpProfile = Optional.ofNullable(mProfileManager)
                .map(MBBluetoothProfileManager::getA2dpProfile)
                .orElse(null);

        if (a2dpProfile == null) {
            logI("isScmstEnabled: a2dpProfile is null, return false.");
            return false;
        }

        if (!mProfiles.contains(a2dpProfile)) {
            logI("isScmstEnabled: does NOT support a2dp, return false.");
            return false;
        }

        return a2dpProfile.isScmstEnabled(this);
    }

    private void logD(String msg) {
        LOG.d(mAddress + " [" + mName + "] " + msg);
    }

    private void logI(String msg) {
        LOG.i(mAddress + " [" + mName + "] " + msg);
    }
}
