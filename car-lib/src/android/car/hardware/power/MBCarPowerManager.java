package android.car.hardware.power;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.MBCarTransactionException;
import android.car.VehicleAreaType;
import android.car.annotation.AddedInOrBefore;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.vendorvehicleproperty.MBVehiclePropertyIds;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

/**
 * Provides power state and policy from AOSP CarPowerManager
 * and user mode state from Mercedes-Benz customization.
 *
 * @Author: HUJIANF
 * @CreateTime: 2023/5/24
 * @Email: jianfei.humercedes-benz.com
 * Extends {@link CarManagerBase}.
 */
public class MBCarPowerManager extends CarManagerBase {

    public static final String TAG = "MB_VF_CarPowerManager";

    // The following power state definitions must match the ones located in the native
    // CarPowerManager: packages/services/Car/car-lib/native/include/CarPowerManager.h
    /**
     * Power state to represent the current one is unavailable, unknown, or invalid.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_INVALID = CarPowerManager.STATE_INVALID;

    /**
     * Power state to represent Android is up, but waits for the vendor to give a signal to start
     * main functionality.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_WAIT_FOR_VHAL = CarPowerManager.STATE_WAIT_FOR_VHAL;

    /**
     * Power state to represent the system enters deep sleep (suspend to RAM).
     *
     * <p>In case of using {@link CarPowerManager.CarPowerStateListenerWithCompletion}, the timeout for suspend
     * enter is 5 seconds by default and can be configured by setting
     * {@code config_shutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_SUSPEND_ENTER = CarPowerManager.STATE_SUSPEND_ENTER;

    /**
     * Power state to represent the system wakes up from suspend.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_SUSPEND_EXIT = CarPowerManager.STATE_SUSPEND_EXIT;

    /**
     * Power state to represent the system enters shutdown state.
     *
     * <p>In case of using {@link CarPowerManager.CarPowerStateListenerWithCompletion}, the timeout for shutdown
     * enter is 5 seconds by default and can be configured by setting
     * {@code config_shutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_SHUTDOWN_ENTER = CarPowerManager.STATE_SHUTDOWN_ENTER;

    /**
     * Power state to represent the system is at on state.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_ON = CarPowerManager.STATE_ON;

    /**
     * Power state to represent the system is getting ready for shutdown or suspend. Application is
     * expected to cleanup and be ready to suspend.
     *
     * <p>The maximum duration of shutdown preprare is 15 minutes by default, and can be increased
     * by setting {@code maxGarageModeRunningDurationInSecs} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_SHUTDOWN_PREPARE = CarPowerManager.STATE_SHUTDOWN_PREPARE;

    /**
     * Power state to represent shutdown is cancelled, returning to normal state.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_SHUTDOWN_CANCELLED = CarPowerManager.STATE_SHUTDOWN_CANCELLED;

    /**
     * Power state to represent the system enters hibernation (suspend to disk) state.
     *
     * <p>In case of using {@link CarPowerManager.CarPowerStateListenerWithCompletion}, the timeout for hibernation
     * enter is 5 seconds by default and can be configured by setting
     * {@code config_shutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_HIBERNATION_ENTER = CarPowerManager.STATE_HIBERNATION_ENTER;

    /**
     * Power state to represent the system wakes up from hibernation.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_HIBERNATION_EXIT = CarPowerManager.STATE_HIBERNATION_EXIT;

    /**
     * Power state to represent system shutdown is initiated, but output components such as display
     * is still on. UI to show a device is about to shutdown can be presented at this state.
     *
     * <p>In case of using {@link CarPowerManager.CarPowerStateListenerWithCompletion}, the timeout for pre shutdown
     * prepare is 5 seconds by default and can be configured by setting
     * {@code config_preShutdownPrepareTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_PRE_SHUTDOWN_PREPARE = CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE;

    /**
     * Power state to represent car power management service and VHAL finish processing to enter
     * deep sleep and the device is about to sleep.
     *
     * <p>In case of using {@link CarPowerManager.CarPowerStateListenerWithCompletion}, the timeout for post suspend
     * enter is 5 seconds by default and can be configured by setting
     * {@code config_postShutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    @AddedInOrBefore(majorVersion = 33)
    public static final int STATE_POST_SUSPEND_ENTER = CarPowerManager.STATE_POST_SUSPEND_ENTER;

    /**
     * Power state to represent car power management service and VHAL finish processing to shutdown
     * and the device is about to power off.
     *
     * <p>In case of using {@link CarPowerManager.CarPowerStateListenerWithCompletion}, the timeout for post
     * shutdown enter is 5 seconds by default and can be configured by setting
     * {@code config_postShutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_POST_SHUTDOWN_ENTER = CarPowerManager.STATE_POST_SHUTDOWN_ENTER;

    /**
     * Power state to represent car power management service and VHAL finish processing to enter
     * hibernation and the device is about to hibernate.
     *
     * <p>In case of using {@link CarPowerManager.CarPowerStateListenerWithCompletion}, the timeout for post
     * hibernation enter is 5 seconds by default and can be configured by setting
     * {@code config_postShutdownEnterTimeout} in the car service resource.
     *
     * @hide
     */
    @SystemApi
    public static final int STATE_POST_HIBERNATION_ENTER = CarPowerManager.STATE_POST_HIBERNATION_ENTER;
    /**
     * Left-back screen display.
     */
    public static final int DISPLAY_IDENTIFIER_REAR_LEFT = 0;
    /**
     * Right-back screen display.
     */
    public static final int DISPLAY_IDENTIFIER_REAR_RIGHT = 1;
    /**
     * The screen is closed status.
     */
    public static final int DISPLAY_STATUS_OFF = 0;
    /**
     * The screen is open status.
     */
    public static final int DISPLAY_STATUS_ON = 1;
    /**
     * The screen is default status.
     */
    public static final int DISPLAY_STATUS_DEFAULT = -1;
    /**
     * User selection mode is none.
     */
    public static final int SELECTED_NONE = 0;
    /**
     * User selection mode is display off.
     */
    public static final int SELECTED_DISPLAY_OFF = 1;
    /**
     * User selection mode is system off.
     */
    public static final int SELECTED_SYSTEM_OFF = 2;
    /**
     * User selection mode is removed confirm.
     */
    public static final int SELECTED_KEY_REMOVED_CONFIRM = 3;
    /**
     * User selection mode is removed continue.
     */
    public static final int SELECTED_KEY_REMOVED_CONTINUE = 4;
    /**
     * User selection mode is exit display off.
     */
    public static final int SELECTED_EXIT_DISPLAY_OFF = 5;
    /**
     * User selection mode is system restart.
     */
    public static final int SELECTED_RESTART_MSOC_SYSTEM = 6;
    /**
     * User selection mode is system restart.
     */
    public static final int SELECTED_EXIT_SYSTEM_OFF = 7;

    /**
     * StateCode none
     */
    public static final int STATE_CODE_NONE = 0;
    /**
     * Specified Device not recognized
     */
    public static final int STATE_CODE_TIME_MIN_TIMER_EXPIRED = 1;
    /**
     * Welcome animation abortion
     */
    public static final int STATE_CODE_ANIMATION_ABORTION = 2;
    /**
     * Screen state ON
     */
    public static final int STATE_CODE_CI_SCREEN_STATE_ON = 3;
    /**
     * Screen state OFF
     */
    public static final int STATE_CODE_CI_SCREEN_STATE_OFF = 4;
    /**
     * Screen state NONE
     */
    public static final int STATE_CODE_CI_SCREEN_STATE_NONE = 5;
    /**
     * Constant representing an undefined switch-up reason.
     */
    public static final int SWITCHUP_REASON_UNDEFINED = 0;

    /**
     * Constant representing the switch-up reason for HUONSTAT.
     */
    public static final int SWITCHUP_REASON_HUONSTAT = 1;

    /**
     * Constant representing the switch-up reason for HKON.
     */
    public static final int SWITCHUP_REASON_HKON = 2;

    /**
     * Constant representing the switch-up reason for DIAG.
     */
    public static final int SWITCHUP_REASON_DIAG = 3;

    /**
     * Constant representing the switch-up reason for VEHUNLOCK.
     */
    public static final int SWITCHUP_REASON_VEHUNLOCK = 4;

    /**
     * Constant representing an undefined weak-up reason.
     */
    public static final int WEAKUP_REASON_UNDEFINED = 0;

    /**
     * Constant representing the weak-up reason for CAN.
     */
    public static final int WEAKUP_REASON_CAN = 1;

    /**
     * Constant representing the weak-up reason for ETHERNET.
     */
    public static final int WEAKUP_REASON_ETHERNET = 2;

    /**
     * Constant representing the weak-up reason for HKON_RL.
     */
    public static final int WEAKUP_REASON_HKON_RL = 3;

    /**
     * Constant representing the weak-up reason for HKON_RR.
     */
    public static final int WEAKUP_REASON_HKON_RR = 4;

    /**
     * Constant representing an undefined shutdown scenario.
     */
    public static final int SHUTDOWN_SCENARIO_UNDEFINED = 0;

    /**
     * Constant representing shutdown scenario 1.
     */
    public static final int SHUTDOWN_SCENARIO_1 = 1;

    /**
     * Constant representing shutdown scenario 2.
     */
    public static final int SHUTDOWN_SCENARIO_2 = 2;

    /**
     * Constant representing shutdown scenario 3.
     */
    public static final int SHUTDOWN_SCENARIO_3 = 3;

    /**
     * Constant representing shutdown scenario 4.
     */
    public static final int SHUTDOWN_SCENARIO_4 = 4;

    /**
     * Constant representing shutdown scenario BLOCKED.
     */
    public static final int SHUTDOWN_SCENARIO_BLOCKED = 5;

    /**
     * Constant representing shutdown scenario 5.
     */
    public static final int SHUTDOWN_SCENARIO_5 = 6;

    /**
     * Constant representing shutdown scenario 5_HM.
     */
    public static final int SHUTDOWN_SCENARIO_5_HM = 7;

    /**
     * Constant representing shutdown scenario PREPAREFORPOWEROFF.
     */
    public static final int SHUTDOWN_SCENARIO_PREPAREFORPOWEROFF = 8;

    /**
     * Constant representing shutdown scenario FUS.
     */
    public static final int SHUTDOWN_SCENARIO_FUS = 9;

    /**
     * Constant representing shutdown scenario NOTACTIVE.
     */
    public static final int SHUTDOWN_SCENARIO_NOTACTIVE = 10;

    /**
     * Constant representing an undefined animation action type.
     */
    public static final int ANIMATION_ACTION_TYPE_UNDEFINED = 0;

    /**
     * Constant representing the animation action type START.
     */
    public static final int ANIMATION_ACTION_TYPE_START = 1;

    /**
     * Constant representing the animation action type LOGO.
     */
    public static final int ANIMATION_ACTION_TYPE_LOGO = 2;

    /**
     * Constant representing the animation action type EXIT.
     */
    public static final int ANIMATION_ACTION_TYPE_EXIT = 3;

    /**
     * Constant representing the state where display content request is enabled.
     */
    public static final int DISPCONTENTREQ_ON = 0;

    /**
     * Constant representing the state where display content request is disabled.
     */
    public static final int DISPCONTENTREQ_OFF = 1;
    /**
     * Constant representing the state where the display position is inactive.
     */
    public static final int DISPPOSNSTATE_INACTIVE_POSN = 0;

    /**
     * Constant representing the state where the display position is active.
     */
    public static final int DISPPOSNSTATE_ACTIVE_POSN = 1;

    /**
     * Constant representing the state where the display position provides informational content.
     */
    public static final int DISPPOSNSTATE_INFO_POSN = 2;

    /**
     * Constant representing an intermediate state of the display position.
     */
    public static final int DISPPOSNSTATE_INTERMEDIATE = 3;

    /**
     * Constant representing the state where the display position is in the process of opening.
     */
    public static final int DISPPOSNSTATE_OPENING = 4;

    /**
     * Constant representing the state where the display position is in the process of closing.
     */
    public static final int DISPPOSNSTATE_CLOSING = 5;

    /**
     * Constant representing the state where the display position has been released.
     */
    public static final int DISPPOSNSTATE_RELEASED = 6;

    /**
     * Constant representing the start of the agent's welcome animation.
     */
    public static final int ANIMATION_AGENT_WELCOME_START = 0;

    /**
     * Constant representing the end of the agent's welcome animation.
     */
    public static final int ANIMATION_AGENT_WELCOME_END = 1;

    /**
     * Constant representing the start of the agent's goodbye animation.
     */
    public static final int ANIMATION_AGENT_GOODBYE_START = 2;

    /**
     * Constant representing the end of the agent's goodbye animation.
     */
    public static final int ANIMATION_AGENT_GOODBYE_END = 3;

    /**
     * User Mode Message Definition.
     */
    private static final int MSG_HANDLER_USER_MODE = 0;
    /**
     * Hardkey pressed Message Definition.
     */
    private static final int MSG_HANDLER_HK_PRESSED = 1;
    /**
     * Vehicle key remove Message Definition.
     */
    private static final int MSG_HANDLER_VEHICLE_KEY_REMOVED = 2;
    /**
     * Critical temperature Message Definition.
     */
    private static final int MSG_HANDLER_CRITICAL_TEMPERATURE = 3;
    /**
     * Animation Screen State Message Definition.
     */
    private static final int MSG_HANDLER_ANIMATION_SCREEN_STATE = 4;
    /**
     * Select mode from remote civic.
     */
    private static final int MSG_HANDLER_SELECETMODE_REMOTE = 5;

    /**
     * Display Mode Message Definition.
     */
    private static final int MSG_HANDLER_DISPLAY_MODE = 6;
    /**
     * Message handler code for agent on/off status events.
     */
    private static final int MSG_HANDLER_ONOFF_AGENT_STATUS = 7;

    /**
     * RenderUIState,content OFF	Display content active status
     */
    public static final int OFF = 0;
    /**
     * RenderUIState,split screen content on left side ON
     */
    public static final int ON_LEFT = 1;
    /**
     * RenderUIState,split screen content on right side ON
     */
    public static final int ON_RIGHT = 2;
    /**
     * RenderUIState,content ON
     */
    public static final int ON_ALL = 3;
    /**
     * RenderUIState,info display ON
     */
    public static final int ON_INFO = 4;
    /**
     * RenderUIState,ON_BYE
     */
    private static final int ON_BYE = 5;
    private final IMBCarPower mService;
    private final CarPowerManager mCarPowerManager;
    private final CarPropertyManager mCarPropertyManager;
    private final CarPowerPolicyFilter mFilter = new CarPowerPolicyFilter.Builder()
            .setComponents(
                    PowerComponent.AUDIO,
                    PowerComponent.BLUETOOTH,
                    PowerComponent.DISPLAY,
                    PowerComponent.MEDIA,
                    PowerComponent.WIFI,
                    PowerComponent.MICROPHONE
            )
            .build();
    private final UserModeCallbackHandler mCallbackHandler;
    /**
     * Listener for user mode events.
     */
    private CarUserModeListener mUserModeListener;
    /**
     * Listener for display mode events.
     */
    private CarDisplayModeListener mDisplayModeListener;
    /**
     * Listener for HK pressed events.
     */
    private CarHKPressedListener mHKPressedListener;
    /**
     * Listener for vehicle key removed events.
     */
    private CarVehicleKeyRemovedListener mVehicleKeyRemovedListener;
    /**
     * Listener for critical temperature events.
     */
    private CarCriticalTemperatureListener mCriticalTemperatureListener;
    /**
     * Listener for animation screen state events.
     */
    private AnimationScreenStateListener mAnimationScreenStateListener;
    /**
     * Listener for shutdown scenario events.
     */
    private CarShutdownScenarioListener mShutdownScenarioListener;
    /**
     * Listener for remote select mode events.
     */
    private CarRemoteSelectModeListener mCarRemoteSelectModeListener;
    /**
     * Listener for handling Car On/Off Agent status changes.
     */
    private CarOnOffAgentStatusListener mOnOffAgentStatusListener;
    /**
     * Executor for user mode events.
     */
    private Executor mUserModeExecutor;
    /**
     * Executor for display mode events.
     */
    private Executor mDisplayModeExecutor;
    /**
     * Executor for HK pressed events.
     */
    private Executor mHKPressedExecutor;
    /**
     * Executor for vehicle key removed events.
     */
    private Executor mVehicleKeyRemovedExecutor;
    /**
     * Executor for critical temperature events.
     */
    private Executor mCriticalTemperatureExecutor;
    /**
     * Executor for animation screen state events.
     */
    private Executor mAnimationScreenStateExecutor;
    /**
     * Executor for shutdown scenario events.
     */
    private Executor mShutdownScenarioExecutor;
    /**
     * Executor for remote select mode events.
     */
    private Executor mRemoteSelectModeExecutor;
    /**
     * Executor used for running tasks related to Car On/Off Agent status changes.
     */
    private Executor mOnOffAgentStatusExecutor;
    /**
     * Listener to service for user mode events.
     */
    private CarUserModeListenerToService mListenerToService;

    private final CarPropertyManager.CarPropertyEventCallback mPropertyCallback = new CarPropertyManager.CarPropertyEventCallback() {
        @Override
        public void onChangeEvent(CarPropertyValue value) {
            switch (value.getPropertyId()) {
                case MBVehiclePropertyIds.VENDOR_POWER_SHUTDOWN_SCENARIO: {
                    CarShutdownScenarioListener listener;
                    Executor executor;

                    synchronized (this) {
                        listener = mShutdownScenarioListener;
                        executor = mShutdownScenarioExecutor;
                    }

                    if ((listener != null) && (executor != null)) {
                        Integer[] val = (Integer[]) value.getValue();
                        executor.execute(() -> listener.onShutdownScenarioNotify(val[0]));
                    }

                    break;
                }

                case MBVehiclePropertyIds.VENDOR_POWER_ANIMATION_AGENT_STATUS: {
                    AnimationScreenStateListener listener;
                    Executor executor;

                    synchronized (this) {
                        listener = mAnimationScreenStateListener;
                        executor = mAnimationScreenStateExecutor;
                    }

                    if ((listener != null) && (executor != null)) {
                        Integer[] val = (Integer[]) value.getValue();
                        executor.execute(() -> listener.onAnimationAgentStatusNotify(val[0]));
                    }

                    break;
                }

                default:
                    break;
            }
        }

        @Override
        public void onErrorEvent(int propId, int zone) {

        }
    };

    /**
     * Gets an instance of the MBCarPowerManager.
     */
    public MBCarPowerManager(Car car, IBinder userservice) {
        super(car);
        Log.v(TAG, "MBCarPowerManager");
        mCarPowerManager = (CarPowerManager) mCar.getCarManager(Car.POWER_SERVICE);
        mCarPropertyManager = (CarPropertyManager) mCar.getCarManager(Car.PROPERTY_SERVICE);
        mService = IMBCarPower.Stub.asInterface(userservice);
        mCallbackHandler = new UserModeCallbackHandler(this, getEventHandler().getLooper());
    }

    /**
     * Sets a listener to receive power state changes. Only one listener may be set at a
     * time for an instance of CarPowerManager.
     *
     * <p>The listener is assumed to completely handle the {@code onStateChanged} before returning.
     *
     * @param powerStateListener The listener which will receive the power state change.
     * @throws IllegalStateException    When a listener is already set for the power state change.
     * @throws IllegalArgumentException When the given listener is null.
     * @hide
     */
    public synchronized void registerPowerStateListener(@NonNull @CallbackExecutor Executor executor,
                                                        @NonNull CarPowerManager.CarPowerStateListener powerStateListener) {
        Log.d(TAG, "register power state listener");

        if (mCarPowerManager == null) {
            Log.e(TAG, "power manager is null");
            return;
        }

        mCarPowerManager.setListener(executor, powerStateListener);
    }

    /**
     * Sets a listener to receive power state changes. Only one listener may be set at a time for an
     * instance of CarPowerManager.
     *
     * <p>For calls that require completion before continue, we attach a
     * {@link CarPowerManager.CompletablePowerStateChangeFuture} which is being used as a signal that caller is
     * finished and ready to proceed.
     * Once the future is completed, car power management service knows that the application has
     * handled the power state transition and moves to the next state.
     *
     * @param powerStateListener The listener which will receive the power state change.
     * @throws IllegalStateException    When a listener is already set for the power state change.
     * @throws IllegalArgumentException When the given listener is null.
     * @hide
     */
    public synchronized void registerPowerStateListenerWithCompletion(@NonNull @CallbackExecutor Executor executor,
                                                                      @NonNull CarPowerManager.CarPowerStateListenerWithCompletion powerStateListener) {
        Log.d(TAG, "register power state listener With Completion");

        if (mCarPowerManager == null) {
            Log.e(TAG, "power manager is null");
            return;
        }

        mCarPowerManager.setListenerWithCompletion(executor, powerStateListener);
    }

    /**
     * Registers a power policy listener with the specified executor.
     *
     * @param executor            The executor to handle callback events.
     * @param powerPolicyListener The power policy listener to be registered.
     * @hide
     */
    public synchronized void registerPowerPolicyListener(@NonNull @CallbackExecutor Executor executor,
                                                         @NonNull CarPowerManager.CarPowerPolicyListener powerPolicyListener) {
        Log.d(TAG, "register power policy listener");

        if (mCarPowerManager == null) {
            Log.e(TAG, "power manager is null");
            return;
        }

        mCarPowerManager.addPowerPolicyListener(executor, mFilter, powerPolicyListener);
    }

    /**
     * Registers a user mode listener with the specified executor and display identifier.
     *
     * @param executor         The executor to handle callback events.
     * @param userModeListener The user mode listener to be registered.
     * @throws MBCarTransactionException Throws an exception if there is a transaction error.
     * @hide
     */
    public synchronized void registerUserModeListener(@NonNull @CallbackExecutor Executor executor,
                                                      @NonNull CarUserModeListener userModeListener)
            throws MBCarTransactionException {
        if (mService == null) {
            Log.e(TAG, "service is null");
            return;
        }

        Log.d(TAG, "register user mode listener");
        mUserModeExecutor = executor;
        mUserModeListener = userModeListener;

        try {
            if (mListenerToService == null) {
                mListenerToService = new CarUserModeListenerToService(this);
            }

            mService.registerUserModeListener(mListenerToService);

        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Registers a display mode listener with the specified executor and display identifier.
     *
     * @param executor            The executor to handle callback events.
     * @param displayModeListener The display mode listener to be registered.
     * @throws MBCarTransactionException Throws an exception if there is a transaction error.
     * @hide
     */
    public synchronized void registerDisplayModeListener(@NonNull @CallbackExecutor Executor executor,
                                                         @NonNull CarDisplayModeListener displayModeListener)
            throws MBCarTransactionException {
        if (mService == null) {
            Log.e(TAG, "service is null");
            return;
        }

        Log.d(TAG, "register display mode listener");
        mDisplayModeExecutor = executor;
        mDisplayModeListener = displayModeListener;

        try {
            if (mListenerToService == null) {
                mListenerToService = new CarUserModeListenerToService(this);
            }

            mService.registerUserModeListener(mListenerToService);

        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Registers a Hard Key (HK) pressed listener with the specified executor and display identifier.
     *
     * @param executor          The executor to handle callback events.
     * @param hKPressedListener The HU pressed listener to be registered.
     * @throws MBCarTransactionException Throws an exception if there is a transaction error.
     * @hide
     */
    public synchronized void registerHKPressedListener(@NonNull @CallbackExecutor Executor executor,
                                                       @NonNull CarHKPressedListener hKPressedListener)
            throws MBCarTransactionException {
        if (mService == null) {
            Log.e(TAG, "service is null");
            return;
        }

        Log.d(TAG, "register HKPressed listener, display");
        mHKPressedExecutor = executor;
        mHKPressedListener = hKPressedListener;

        try {
            if (mListenerToService == null) {
                mListenerToService = new CarUserModeListenerToService(this);
            }

            mService.registerUserModeListener(mListenerToService);

        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Registers a Vehicle Key Removed listener with the specified executor, display identifier, and listener.
     *
     * @param executor                  The executor to handle callback events.
     * @param vehicleKeyRemovedListener The Vehicle Key Removed listener to be registered.
     * @throws MBCarTransactionException Throws an exception if there is a transaction error.
     * @hide
     */
    public synchronized void registerVehicleKeyRemovedListener(@NonNull @CallbackExecutor Executor executor,
                                                               @NonNull CarVehicleKeyRemovedListener vehicleKeyRemovedListener)
            throws MBCarTransactionException {
        if (mService == null) {
            Log.e(TAG, "service is null");
            return;
        }

        Log.d(TAG, "register Vehicle Key Removed listener, display");
        mVehicleKeyRemovedExecutor = executor;
        mVehicleKeyRemovedListener = vehicleKeyRemovedListener;

        try {
            if (mListenerToService == null) {
                mListenerToService = new CarUserModeListenerToService(this);
            }

            mService.registerUserModeListener(mListenerToService);

        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Registers a Critical Temperature listener with the specified executor, display identifier, and listener.
     *
     * @param executor                    The executor to handle callback events.
     * @param criticalTemperatureListener The Critical Temperature listener to be registered.
     * @throws MBCarTransactionException Throws an exception if there is a transaction error.
     * @hide
     */
    public synchronized void registerCriticalTemperatureListener(@NonNull @CallbackExecutor Executor executor,
                                                                 @NonNull CarCriticalTemperatureListener criticalTemperatureListener)
            throws MBCarTransactionException {
        if (mService == null) {
            Log.e(TAG, "service is null");
            return;
        }

        Log.d(TAG, "register critical temperature listener, display ");
        mCriticalTemperatureExecutor = executor;
        mCriticalTemperatureListener = criticalTemperatureListener;

        try {
            if (mListenerToService == null) {
                mListenerToService = new CarUserModeListenerToService(this);
            }

            mService.registerUserModeListener(mListenerToService);

        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Registers a Shutdown Scenario listener with the specified executor and listener.
     *
     * @param executor                 The executor to handle callback events.
     * @param shutdownScenarioListener The Shutdown Scenario listener to be registered.
     * @throws MBCarTransactionException Throws an exception if there is a transaction error.
     * @hide
     */
    public synchronized void registerShutdownScenarioListener(@NonNull @CallbackExecutor Executor executor,
                                                              @NonNull CarShutdownScenarioListener shutdownScenarioListener)
            throws MBCarTransactionException {
        Log.d(TAG, "register shutdown Scenario Listener");
        if (mCarPropertyManager == null) {
            Log.e(TAG, "power manager is null");
            return;
        }

        mShutdownScenarioExecutor = executor;
        mShutdownScenarioListener = shutdownScenarioListener;

        mCarPropertyManager.registerCallback(mPropertyCallback, MBVehiclePropertyIds.VENDOR_POWER_SHUTDOWN_SCENARIO, 0);
    }

    /**
     * Registers an Animation Screen State listener with the specified executor, display identifier, and listener.
     *
     * @param executor                     The executor to handle callback events.
     * @param animationScreenStateListener The Animation Screen State listener to be registered.
     * @throws MBCarTransactionException Throws an exception if there is a transaction error.
     * @hide
     */
    public synchronized void registerAnimationScreenStateListener(@NonNull @CallbackExecutor Executor executor,
                                                                  @NonNull AnimationScreenStateListener animationScreenStateListener)
            throws MBCarTransactionException {
        if (mService == null) {
            Log.e(TAG, "service is null");
            return;
        }

        Log.d(TAG, "registerAnimationScreenStateListener register Animation Screen State listener");
        mAnimationScreenStateExecutor = executor;
        mAnimationScreenStateListener = animationScreenStateListener;

        try {
            if (mCarPropertyManager != null) {
                mCarPropertyManager.registerCallback(mPropertyCallback, MBVehiclePropertyIds.VENDOR_POWER_ANIMATION_AGENT_STATUS, 0);
            }

            if (mListenerToService == null) {
                mListenerToService = new CarUserModeListenerToService(this);
            }

            mService.registerUserModeListener(mListenerToService);

        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Registers a listener for remote user-selected mode changes.
     *
     * @param executor                 The executor on which the callback should be invoked.
     * @param remoteSelectModeListener The listener for remote user-selected mode changes.
     * @throws MBCarTransactionException If there is an exception during the registration transaction.
     */
    public synchronized void registerRemoteSelectModeListener(@NonNull @CallbackExecutor Executor executor,
                                                              @NonNull CarRemoteSelectModeListener remoteSelectModeListener)
            throws MBCarTransactionException {

        if (mService == null) {
            Log.e(TAG, "service is null");
            return;
        }

        Log.d(TAG, "register Remote Select Mode listener");
        mRemoteSelectModeExecutor = executor;
        mCarRemoteSelectModeListener = remoteSelectModeListener;

        try {
            if (mListenerToService == null) {
                mListenerToService = new CarUserModeListenerToService(this);
            }

            mService.registerUserModeListener(mListenerToService);

        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Registers a listener to receive notifications about Car On/Off Agent status changes.
     *
     * @param executor                 The executor used for running the listener callbacks.
     * @param onOffAgentStatusListener The listener to be registered for Car On/Off Agent status changes.
     * @throws MBCarTransactionException If there is an error during the registration process.
     */
    public synchronized void registerCarOnOffAgentStatusListener(@NonNull @CallbackExecutor Executor executor,
                                                                 @NonNull CarOnOffAgentStatusListener onOffAgentStatusListener)
            throws MBCarTransactionException {

        if (mService == null) {
            Log.e(TAG, "service is null");
            return;
        }

        Log.d(TAG, "register OnOff Agent Status listener");
        mOnOffAgentStatusExecutor = executor;
        mOnOffAgentStatusListener = onOffAgentStatusListener;

        try {
            if (mListenerToService == null) {
                mListenerToService = new CarUserModeListenerToService(this);
            }

            mService.registerUserModeListener(mListenerToService);

        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Unregisters the listener associated with this manager.
     *
     * @hide
     */
    public synchronized void unregisterListener() throws MBCarTransactionException {
        Log.v(TAG, "unregisterListener");
        if (mService == null || mCarPowerManager == null) {
            Log.e(TAG, "user mode service is null");
            return;
        }

        try {
            mCarPowerManager.clearListener();

            if (mListenerToService != null) {
                mService.unregisterUserModeListener(mListenerToService);
            }

            mCarPropertyManager.unregisterCallback(mPropertyCallback);

        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Retrieves the current user mode for the specified display identifier.
     *
     * @param displayIdentifier The identifier of the display.
     * @return The current user mode for the specified display.
     * @hide
     */
    public synchronized @DISPLAY_STATUS int getCurrentUserMode(@DISPLAY_IDENTIFIER int displayIdentifier) throws MBCarTransactionException {
        Log.v(TAG, "getCurrentUserMode");
        if (mService == null) {
            Log.e(TAG, "user mode service is null");
            return DISPLAY_STATUS_DEFAULT;
        }

        try {
            return mService.getCurrentUserMode(displayIdentifier);
        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Retrieves the current display mode for the specified display identifier.
     *
     * @param displayIdentifier The identifier of the display.
     * @return The current display mode for the specified display.
     * @hide
     */
    public synchronized @DISPLAY_STATUS int getCurrentDisplayMode(@DISPLAY_IDENTIFIER int displayIdentifier) throws MBCarTransactionException {
        Log.v(TAG, "getCurrentDisplayMode");
        if (mService == null) {
            Log.e(TAG, "user mode service is null");
            return DISPLAY_STATUS_DEFAULT;
        }

        try {
            return mService.getCurrentDisplayMode(displayIdentifier);
        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Retrieves the current state of the display position.
     *
     * @return The current display position state, as defined by {@code @DISPPOSNSTATE}.
     * @throws MBCarTransactionException If there is an error retrieving the current display position state.
     */
    public int getCurrentDispPosnState() throws MBCarTransactionException {
        Log.v(TAG, "getCurrentDispPosnState");
        if (mService == null) {
            Log.e(TAG, "user mode service is null");
            return DISPLAY_STATUS_DEFAULT;
        }

        try {
            return mService.getCurrentDispPosnState();
        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Retrieves the current request for display content.
     *
     * @return The current display content request state, as defined by {@code @DISPCONTENTREQ}.
     * @throws MBCarTransactionException If there is an error retrieving the current display content request.
     */
    public int getCurrentDispContentReq() throws MBCarTransactionException {
        Log.v(TAG, "getCurrentDispContentReq");
        if (mService == null) {
            Log.e(TAG, "user mode service is null");
            return DISPLAY_STATUS_DEFAULT;
        }

        try {
            return mService.getCurrentDispContentReq();
        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Sets the user-selected Hard Keyt (HK) mode for the specified display identifier.
     *
     * @param displayIdentifier The identifier of the display.
     * @param status            The user-selected HK mode.
     * @return True if the operation is successful, false otherwise.
     * @hide
     */
    public synchronized boolean setUserSelectHKMode(@DISPLAY_IDENTIFIER int displayIdentifier, @USER_SELECTED_HK_MODE int status) {
        if (mService == null) {
            Log.e(TAG, "user mode service is null");
            return false;
        }

        try {
            Log.i(TAG, "setUserSelectHKMode, display : " + displayIdentifier + ", status : " + status);
            return mService.setUserSelectHKMode(displayIdentifier, status);
        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Sets the user-selected key-removed mode for the specified display identifier.
     *
     * @param displayIdentifier The identifier of the display.
     * @param status            The user-selected key-removed mode.
     * @return True if the operation is successful, false otherwise.
     * @hide
     */
    public synchronized boolean setUserSelectKeyRemovedMode(@DISPLAY_IDENTIFIER int displayIdentifier, @USER_SELECTED_KEY_REMOVED int status) {
        if (mService == null) {
            Log.e(TAG, "user mode service is null");
            return false;
        }

        try {
            Log.i(TAG, "setUserSelectKeyRemovedMode, display : " + displayIdentifier + ", status : " + status);
            return mService.setUserSelectKeyRemovedMode(displayIdentifier, status);
        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Sets the animation action type for the specified display identifier.
     *
     * @param type              The animation action type.
     * @param displayIdentifier The identifier of the display.
     */
    public synchronized void setAnimationActionType(@ANIMATION_ACTION_TYPE int type, @DISPLAY_IDENTIFIER int displayIdentifier) {
        if (mCarPropertyManager == null) {
            Log.e(TAG, "property manager is null");
            return;
        }

        Log.i(TAG, "setAnimationActionType, display : " + displayIdentifier + ", type : " + type);
        mCarPropertyManager.setProperty(Integer[].class, MBVehiclePropertyIds.VENDOR_POWER_EXIT_ANIMATION, 0, new Integer[]{type, displayIdentifier});
    }

    /**
     * setRenderUIState will be called by QNX, used to notify VCPU that the UI State
     * add for VAN
     *
     * @param  renderUIState render UI State
     * @return True if the operation is successful, false otherwise.
     * @hide
     */
    public synchronized boolean setRenderUIState(@RENDER_UI_STATE int renderUIState) {
        if (mService == null) {
            Log.e(TAG, "user mode service is null");
            return false;
        }

        try {
            Log.i(TAG, "setRenderUIState, renderUIState : " + renderUIState);
            return mService.setRenderUIState(renderUIState);
        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Gets the current power state.
     *
     * @return The current power state.
     * @hide
     */
    public synchronized @PowerState int getCurrentPowerState() {
        Log.v(TAG, "getCurrentPowerState");
        if (mCarPowerManager == null) {
            Log.e(TAG, "power manager is null");
            return STATE_INVALID;
        }

        return mCarPowerManager.getPowerState();
    }

    /**
     * Gets the current power policy.
     *
     * @return The current power policy.
     * @hide
     */
    public synchronized CarPowerPolicy getCurrentPowerPolicy() {
        Log.v(TAG, "getCurrentPowerPolicy");
        if (mCarPowerManager == null) {
            Log.e(TAG, "power manager is null");
            return null;
        }

        return mCarPowerManager.getCurrentPowerPolicy();
    }

    /**
     * Gets the switch-up reason.
     *
     * @return The switch-up reason.
     * @hide
     */
    public synchronized @SWITCHUP_REASON int getSwitchUpReason() {
        Log.v(TAG, "getSwitchUpReason");
        if (mCarPropertyManager == null) {
            Log.e(TAG, "property manager is null");
            return SWITCHUP_REASON_UNDEFINED;
        }

        int[] reason = mCarPropertyManager.getIntArrayProperty(MBVehiclePropertyIds.VENDOR_POWER_SWITCHUP_REASON, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        Log.d(TAG, "getSwitchUpReason : " + reason[0]);
        return reason[0];
    }

    /**
     * Gets the wake-up reason.
     *
     * @return The wake-up reason.
     * @hide
     */
    public synchronized @WEAKUP_REASON int getWeakUpReason() {
        Log.v(TAG, "getWeakUpReason");
        if (mCarPropertyManager == null) {
            Log.e(TAG, "property manager is null");
            return SWITCHUP_REASON_UNDEFINED;
        }

        int[] reason = mCarPropertyManager.getIntArrayProperty(MBVehiclePropertyIds.VENDOR_POWER_WEAKUP_REASON, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        Log.d(TAG, "getWeakUpReason : " + reason[0]);
        return reason[0];
    }

    /**
     * Gets the animation state.
     *
     * @return The animation state.
     * @hide
     */
    public synchronized @STATE_CODE int getAnimationState(@DISPLAY_IDENTIFIER int displayIdentifier) {
        Log.v(TAG, "getAnimationState");
        if (mService == null) {
            Log.e(TAG, "user mode service is null");
            return STATE_CODE_NONE;
        }

        try {
            return mService.getAnimationState(displayIdentifier);
        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    /**
     * Gets the screen state.
     *
     * @return The screen state.
     * @hide
     */
    public synchronized @STATE_CODE int getScreenState(@DISPLAY_IDENTIFIER int displayIdentifier) {
        Log.v(TAG, "getScreenState");
        if (mService == null) {
            Log.e(TAG, "user mode service is null");
            return STATE_CODE_NONE;
        }

        try {
            return mService.getScreenState(displayIdentifier);
        } catch (RemoteException e) {
            throw new MBCarTransactionException(e, "");
        }
    }

    public @ANIMATION_AGENT_STATUS int getAnimationAgentStatus() {
        Log.v(TAG, "getAnimationAgentStatus");
        if (mCarPropertyManager == null) {
            Log.e(TAG, "property manager is null");
            return SWITCHUP_REASON_UNDEFINED;
        }

        int[] status = mCarPropertyManager.getIntArrayProperty(MBVehiclePropertyIds.VENDOR_POWER_ANIMATION_AGENT_STATUS, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        Log.i(TAG, "getAnimationAgentStatus : " + status[0]);
        return status[0];
    }

    private void notifyUserModeFromService(int identifier, int status) {
        Log.v(TAG, "notifyUserModeFromService");
        mCallbackHandler.sendMessage(mCallbackHandler.obtainMessage(MSG_HANDLER_USER_MODE, identifier, status));
    }

    private void notifyDisplayModeFromService(int identifier, int status) {
        Log.v(TAG, "notifyDisplayModeFromService");
        mCallbackHandler.sendMessage(mCallbackHandler.obtainMessage(MSG_HANDLER_DISPLAY_MODE, identifier, status));
    }

    private void notifyHKPressedFromService(int identifier) {
        Log.v(TAG, "notifyHKPressedFromService");
        Message message = mCallbackHandler.obtainMessage();
        message.what = MSG_HANDLER_HK_PRESSED;
        message.arg1 = identifier;
        mCallbackHandler.sendMessage(message);
    }

    private void notifyVehicleKeyRemovedFromService(boolean success) {
        Log.v(TAG, "notifyVehicleKeyRemovedFromService");
        Message message = mCallbackHandler.obtainMessage();
        message.what = MSG_HANDLER_VEHICLE_KEY_REMOVED;
        message.obj = success;
        mCallbackHandler.sendMessage(message);
    }

    private void notifyCriticalTemperatureFromService(boolean isActive) {
        Log.v(TAG, "notifyCriticalTemperatureFromService");
        Message message = mCallbackHandler.obtainMessage();
        message.what = MSG_HANDLER_CRITICAL_TEMPERATURE;
        message.obj = isActive;
        mCallbackHandler.sendMessage(message);
    }

    private void notifyAnimationScreenStateFromService(int animationState, int screenState, int identifier) {
        Log.i(TAG, "notifyAnimationScreenStateFromService, animationState : "
                + animationState + " screenState : " + screenState + " identifier : " + identifier);
        Message message = mCallbackHandler.obtainMessage();
        message.what = MSG_HANDLER_ANIMATION_SCREEN_STATE;
        message.arg1 = animationState;
        message.arg2 = screenState;
        message.obj = identifier;
        mCallbackHandler.sendMessage(message);
    }

    private void notifySelectModeFromRemote(int identifier, int select) {
        Log.v(TAG, "notifySelectModeFromRemote");
        Message message = mCallbackHandler.obtainMessage();
        message.what = MSG_HANDLER_SELECETMODE_REMOTE;
        message.arg1 = identifier;
        message.arg2 = select;
        mCallbackHandler.sendMessage(message);
    }

    private void notifyOnOffAgentStatus(int userModestatus, int dispposnstate, int dispContentReq) {
        Log.v(TAG, "notifyOnOffAgentStatus");
        Message message = mCallbackHandler.obtainMessage();
        message.what = MSG_HANDLER_ONOFF_AGENT_STATUS;
        message.arg1 = userModestatus;
        message.arg2 = dispposnstate;
        message.obj = dispContentReq;
        mCallbackHandler.sendMessage(message);
    }

    private void dispatchUserModeToClient(int identifier, int status) {
        Log.i(TAG, "dispatchUserModeToClient");
        CarUserModeListener listener;
        Executor executor;

        synchronized (this) {
            listener = mUserModeListener;
            executor = mUserModeExecutor;
        }

        if (listener != null && executor != null) {
            executor.execute(() -> listener.onUserModeChanged(identifier, status));
            Log.i(TAG, "dispatchUserModeToClient, identifier = " + identifier + ", status = " + status);
        }
    }

    private void dispatchDisplayModeToClient(int identifier, int status) {
        CarDisplayModeListener listener;
        Executor executor;

        synchronized (this) {
            listener = mDisplayModeListener;
            executor = mDisplayModeExecutor;
        }

        if (listener != null && executor != null) {
            executor.execute(() -> listener.onDisplayModeChanged(identifier, status));
            Log.d(TAG, "dispatchDisplayModeToClient, identifier = " + identifier + ", status = " + status);
        }
    }

    private void dispatchHKPressedToClient(int identifier) {
        CarHKPressedListener listener;
        Executor executor;

        synchronized (this) {
            listener = mHKPressedListener;
            executor = mHKPressedExecutor;
        }

        if (listener != null && executor != null) {
            executor.execute(() -> listener.onHKPressed(identifier));
            Log.d(TAG, "dispatchHKPressedToClient, identifier = " + identifier);
        }
    }

    private void dispatchVehicleKeyRemovedToClient(boolean success) {
        CarVehicleKeyRemovedListener listener;
        Executor executor;

        synchronized (this) {
            listener = mVehicleKeyRemovedListener;
            executor = mVehicleKeyRemovedExecutor;
        }

        if (listener != null && executor != null) {
            executor.execute(() -> listener.onVehicleKeyRemoved(success));
            Log.d(TAG, "dispatchVehicleKeyRemovedToClient, success = " + success);
        }
    }

    private void dispatchCriticalTemperatureToClient(boolean isActive) {
        CarCriticalTemperatureListener listener;
        Executor executor;

        synchronized (this) {
            listener = mCriticalTemperatureListener;
            executor = mCriticalTemperatureExecutor;
        }

        if (listener != null && executor != null) {
            executor.execute(() -> listener.onCriticalTemperature(isActive));
            Log.d(TAG, "dispatchCriticalTemperatureToClient, isActive = " + isActive);
        }
    }

    private void dispatchAnimationScreenToClient(int animationState, int screenState, int identifier) {
        Log.i(TAG, "dispatchAnimationScreenToClient, animationState = " + animationState
                + ", screenState = " + screenState + ", identifier = " + identifier);
        AnimationScreenStateListener listener;
        Executor executor;

        synchronized (this) {
            listener = mAnimationScreenStateListener;
            executor = mAnimationScreenStateExecutor;
        }

        if (listener != null && executor != null) {
            executor.execute(
                    () -> listener.onAnimationScreenStateNotify(animationState, screenState,
                            identifier));
            Log.i(TAG, "dispatchAnimationScreenToClient, animationState = " + animationState
                    + " screenState = " + screenState + " identifier = " + identifier);
        } else {
            if (listener == null) {
                Log.i(TAG, "dispatchAnimationScreenToClient, listener is null ");
            } else {
                Log.i(TAG, "dispatchAnimationScreenToClient, executor is null ");
            }
        }
    }

    private void dispatchSelectModeFromRemote(int identifier, int select) {
        Log.i(TAG, "dispatchSelectModeFromRemote, identifier = " + identifier
                + ", select = " + select);
        CarRemoteSelectModeListener listener;
        Executor executor;

        synchronized (this) {
            listener = mCarRemoteSelectModeListener;
            executor = mRemoteSelectModeExecutor;
        }

        if (listener != null && executor != null) {
            executor.execute(() -> listener.onSelectModeFromRemote(identifier, select));
            Log.d(TAG, "dispatchSelectModeFromRemote, identifier = " + identifier + ", select = " + select);
        }
    }

    private void dispatchOnOffAgentStatus(int userModestatus, int dispposnstate, int dispContentReq) {
        CarOnOffAgentStatusListener listener;
        Executor executor;

        synchronized (this) {
            listener = mOnOffAgentStatusListener;
            executor = mOnOffAgentStatusExecutor;
        }

        if (listener != null && executor != null) {
            executor.execute(() -> listener.onOffAgentStatusEventNotify(userModestatus, dispposnstate, dispContentReq));
            Log.d(TAG, "dispatchOffAgentStatus, userModestatus = " + userModestatus + ", dispposnstate = " + dispposnstate + ", dispContentReq = " + dispContentReq);
        }
    }

    @Override
    protected void onCarDisconnected() {
        mListenerToService = null;
    }

    @IntDef({
            SWITCHUP_REASON_UNDEFINED,
            SWITCHUP_REASON_HUONSTAT,
            SWITCHUP_REASON_HKON,
            SWITCHUP_REASON_DIAG,
            SWITCHUP_REASON_VEHUNLOCK
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SWITCHUP_REASON {
    }

    @IntDef({
            WEAKUP_REASON_UNDEFINED,
            WEAKUP_REASON_CAN,
            WEAKUP_REASON_ETHERNET,
            WEAKUP_REASON_HKON_RL,
            WEAKUP_REASON_HKON_RR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface WEAKUP_REASON {
    }

    @IntDef({
            SHUTDOWN_SCENARIO_UNDEFINED,
            SHUTDOWN_SCENARIO_1,
            SHUTDOWN_SCENARIO_2,
            SHUTDOWN_SCENARIO_3,
            SHUTDOWN_SCENARIO_4,
            SHUTDOWN_SCENARIO_BLOCKED,
            SHUTDOWN_SCENARIO_5,
            SHUTDOWN_SCENARIO_5_HM,
            SHUTDOWN_SCENARIO_PREPAREFORPOWEROFF,
            SHUTDOWN_SCENARIO_FUS,
            SHUTDOWN_SCENARIO_NOTACTIVE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SHUTDOWN_SCENARIO {
    }

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "STATE_", value = {
            STATE_INVALID,
            STATE_WAIT_FOR_VHAL,
            STATE_SUSPEND_ENTER,
            STATE_SUSPEND_EXIT,
            STATE_SHUTDOWN_ENTER,
            STATE_ON,
            STATE_SHUTDOWN_PREPARE,
            STATE_SHUTDOWN_CANCELLED,
            STATE_HIBERNATION_ENTER,
            STATE_HIBERNATION_EXIT,
            STATE_PRE_SHUTDOWN_PREPARE,
            STATE_POST_SUSPEND_ENTER,
            STATE_POST_SHUTDOWN_ENTER,
            STATE_POST_HIBERNATION_ENTER,
    })
    @Target({ElementType.TYPE_USE})
    public @interface PowerState {
    }

    @IntDef({
            DISPLAY_IDENTIFIER_REAR_LEFT,
            DISPLAY_IDENTIFIER_REAR_RIGHT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DISPLAY_IDENTIFIER {
    }

    @IntDef({
            DISPLAY_STATUS_OFF,
            DISPLAY_STATUS_ON,
            DISPLAY_STATUS_DEFAULT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DISPLAY_STATUS {
    }

    @IntDef({
            SELECTED_NONE,
            SELECTED_DISPLAY_OFF,
            SELECTED_SYSTEM_OFF,
            SELECTED_EXIT_DISPLAY_OFF,
            SELECTED_RESTART_MSOC_SYSTEM
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface USER_SELECTED_HK_MODE {
    }

    @IntDef({
            SELECTED_NONE,
            SELECTED_DISPLAY_OFF,
            SELECTED_EXIT_DISPLAY_OFF
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface REMOTE_SELECT_MODE {
    }

    @IntDef({
            SELECTED_NONE,
            SELECTED_KEY_REMOVED_CONFIRM,
            SELECTED_KEY_REMOVED_CONTINUE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface USER_SELECTED_KEY_REMOVED {
    }

    @IntDef({
            ANIMATION_ACTION_TYPE_UNDEFINED,
            ANIMATION_ACTION_TYPE_START,
            ANIMATION_ACTION_TYPE_LOGO,
            ANIMATION_ACTION_TYPE_EXIT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ANIMATION_ACTION_TYPE {
    }

    @IntDef({
            STATE_CODE_NONE,
            STATE_CODE_TIME_MIN_TIMER_EXPIRED,
            STATE_CODE_ANIMATION_ABORTION,
            STATE_CODE_CI_SCREEN_STATE_ON,
            STATE_CODE_CI_SCREEN_STATE_OFF,
            STATE_CODE_CI_SCREEN_STATE_NONE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface STATE_CODE {
    }

    @IntDef({
            DISPCONTENTREQ_ON,
            DISPCONTENTREQ_OFF
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DISPCONTENTREQ {
    }

    @IntDef({
            DISPPOSNSTATE_INACTIVE_POSN,
            DISPPOSNSTATE_ACTIVE_POSN,
            DISPPOSNSTATE_INFO_POSN,
            DISPPOSNSTATE_INTERMEDIATE,
            DISPPOSNSTATE_OPENING,
            DISPPOSNSTATE_CLOSING,
            DISPPOSNSTATE_RELEASED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DISPPOSNSTATE {
    }

    @IntDef({
            ANIMATION_AGENT_WELCOME_START,
            ANIMATION_AGENT_WELCOME_END,
            ANIMATION_AGENT_GOODBYE_START,
            ANIMATION_AGENT_GOODBYE_END,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ANIMATION_AGENT_STATUS {
    }

    @IntDef({
            OFF,
            ON_LEFT,
            ON_RIGHT,
            ON_ALL,
            ON_INFO,
            ON_BYE,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RENDER_UI_STATE {
    }

    /**
     * listener for client register, and notify user mode.
     * {@link DISPLAY_IDENTIFIER,DISPLAY_STATUS}
     *
     * @hide
     */
    public interface CarUserModeListener {
        /**
         * Called when the user mode changes.
         *
         * @param identifier The identifier of the display.
         * @param status The user mode status.
         */
        void onUserModeChanged(@DISPLAY_IDENTIFIER int identifier, @DISPLAY_STATUS int status);
    }

    /**
     * listener for client register, and notify display mode.
     * {@link DISPLAY_IDENTIFIER,DISPLAY_STATUS}
     *
     * @hide
     */
    public interface CarDisplayModeListener {
        /**
         * Called when the display mode changed.
         * @param identifier The display mode identifier.
         * @param status The display mode status.
         */
        void onDisplayModeChanged(@DISPLAY_IDENTIFIER int identifier, @DISPLAY_STATUS int status);
    }

    /**
     * Listener interface for handling HK (Hard Key) pressed events.
     * HK is a proper noun.
     * {@link DISPLAY_IDENTIFIER}
     */
    public interface CarHKPressedListener {
        /**
         * Called when the HK is pressed.
         *
         * @param identifier The identifier of the display.
         */
        void onHKPressed(@DISPLAY_IDENTIFIER int identifier);
    }

    /**
     * Listener interface for handling vehicle key removal events.
     * {@link CarVehicleKeyRemovedListener}
     */
    public interface CarVehicleKeyRemovedListener {
        /**
         * Called when the vehicle key is removed.
         *
         * @param success True if the operation is successful, false otherwise.
         */
        void onVehicleKeyRemoved(boolean success);
    }

    /**
     * Listener interface for handling critical temperature events.
     * {@link #onCriticalTemperature}
     */
    public interface CarCriticalTemperatureListener {
        /**
         * Called when the critical temperature is reached.
         *
         * @param isActive True if the critical temperature is active, false otherwise.
         */
        void onCriticalTemperature(boolean isActive);
    }

    /**
     * Listener interface for handling animation screen state events.
     * {@link STATE_CODE,DISPLAY_IDENTIFIER}
     */
    public interface AnimationScreenStateListener {
        /**
         * Called when animation screen state is changed.
         *
         * @param animationState The animation state.
         * @param screenState The screen state.
         * @param identifier The identifier of the display.
         */
        default void onAnimationScreenStateNotify(@STATE_CODE int animationState, @STATE_CODE int screenState, @DISPLAY_IDENTIFIER int identifier) { }
        /**
         * Called when animation agent status is changed.
         *
         * @param agentStatus The animation agent status.
         */
        default void onAnimationAgentStatusNotify(@ANIMATION_AGENT_STATUS int agentStatus) { }
    }

    /**
     * Listener interface for handling car shutdown scenario events.
     * {@link SHUTDOWN_SCENARIO}
     */
    public interface CarShutdownScenarioListener {
        /**
         * Called when shutdown scenario is changed.
         *
         * @param scenario The shutdown scenario.

         */
        void onShutdownScenarioNotify(@SHUTDOWN_SCENARIO int scenario);
    }

    /**
     * Listener interface for handling remote select mode events.
     * {@link DISPLAY_IDENTIFIER,REMOTE_SELECT_MODE}
     */
    public interface CarRemoteSelectModeListener {
        /**
         * Called when the remote select mode is changed.
         *
         * @param identifier The identifier of the display.
         * @param select The remote select mode.
         */
        void onSelectModeFromRemote(@DISPLAY_IDENTIFIER int identifier, @REMOTE_SELECT_MODE int select);
    }

    /**
     * Listener interface for handling OnOff Agent Status.
     * {@link DISPLAY_STATUS,DISPPOSNSTATE,DISPCONTENTREQ}
     */
    public interface CarOnOffAgentStatusListener {
        /**
         * Called when the OnOff Agent Status is changed.
         *
         * @param userModestatus The user mode status.
         * @param dispposnstate The display position state.
         * @param dispContentReq The display content request.
         */
        void onOffAgentStatusEventNotify(@DISPLAY_STATUS int userModestatus, @DISPPOSNSTATE int dispposnstate, @DISPCONTENTREQ int dispContentReq);
    }

    private static final class UserModeCallbackHandler extends Handler {
        private final WeakReference<MBCarPowerManager> mUserModeMgr;

        public UserModeCallbackHandler(MBCarPowerManager manager, Looper looper) {
            super(looper);
            mUserModeMgr = new WeakReference<>(manager);
        }

        @Override
        public void handleMessage(Message msg) {
            MBCarPowerManager manager = mUserModeMgr.get();
            Log.i(TAG, "handleMessage msg.what:" + msg.what);
            if (manager == null) {
                Log.d(TAG, "manager is null");
                return;
            }

            switch (msg.what) {
                case MSG_HANDLER_USER_MODE:
                    manager.dispatchUserModeToClient(msg.arg1, msg.arg2);
                    break;

                case MSG_HANDLER_HK_PRESSED:
                    manager.dispatchHKPressedToClient(msg.arg1);
                    break;

                case MSG_HANDLER_VEHICLE_KEY_REMOVED:
                    boolean success = (boolean) msg.obj;
                    manager.dispatchVehicleKeyRemovedToClient(success);
                    break;

                case MSG_HANDLER_CRITICAL_TEMPERATURE:
                    boolean isActive = (boolean) msg.obj;
                    manager.dispatchCriticalTemperatureToClient(isActive);
                    break;

                case MSG_HANDLER_ANIMATION_SCREEN_STATE:
                    manager.dispatchAnimationScreenToClient(msg.arg1, msg.arg2, (int) msg.obj);
                    break;

                case MSG_HANDLER_SELECETMODE_REMOTE:
                    manager.dispatchSelectModeFromRemote(msg.arg1, msg.arg2);
                    break;

                case MSG_HANDLER_DISPLAY_MODE:
                    manager.dispatchDisplayModeToClient(msg.arg1, msg.arg2);
                    break;

                case MSG_HANDLER_ONOFF_AGENT_STATUS:
                    manager.dispatchOnOffAgentStatus(msg.arg1, msg.arg2, (int) msg.obj);

                default:
                    Log.d(TAG, "No corresponding message");
                    break;
            }
        }
    }

    private static class CarUserModeListenerToService extends IMBCarPowerListener.Stub {
        private final WeakReference<MBCarPowerManager> mUserModeMgr;
        private MBCarPowerManager manager = null;

        public CarUserModeListenerToService(MBCarPowerManager manager) {
            mUserModeMgr = new WeakReference<>(manager);
        }

        @Override
        public void onUserModeChangedNotify(int identifier, int status) throws android.os.RemoteException {
            Log.i(TAG, "onSelectModeFromRemote");
            manager = mUserModeMgr.get();

            if (manager != null) {
                manager.notifyUserModeFromService(identifier, status);
            }
        }

        @Override
        public void onHKPressedNotify(int identifier) throws android.os.RemoteException {
            manager = mUserModeMgr.get();

            if (manager != null) {
                manager.notifyHKPressedFromService(identifier);
            }
        }

        @Override
        public void onVehicleKeyRemovedNotify(boolean success) throws android.os.RemoteException {
            manager = mUserModeMgr.get();

            if (manager != null) {
                manager.notifyVehicleKeyRemovedFromService(success);
            }
        }

        @Override
        public void onCriticalTemperatureNotify(boolean isActive) throws android.os.RemoteException {
            manager = mUserModeMgr.get();

            if (manager != null) {
                manager.notifyCriticalTemperatureFromService(isActive);
            }
        }

        @Override
        public void onAnimationScreenStateNotify(int animationState, int screenState, int identifier) throws android.os.RemoteException {
            Log.i(TAG, "onAnimationScreenStateNotify, animationState : "
                    + animationState + " screenState : " + screenState + " identifier : "
                    + identifier);
            manager = mUserModeMgr.get();

            if (manager != null) {
                manager.notifyAnimationScreenStateFromService(animationState, screenState, identifier);
            }
            else {
                Log.i(TAG, "onAnimationScreenStateNotify manager is null");
            }

        }

        @Override
        public void onSelectModeFromRemote(int identifier, int select) {
            manager = mUserModeMgr.get();

            if (manager != null) {
                manager.notifySelectModeFromRemote(identifier, select);
            }
        }

        @Override
        public void onDisplayModeChangedNotify(int identifier, int status) throws android.os.RemoteException {
            manager = mUserModeMgr.get();

            if (manager != null) {
                manager.notifyDisplayModeFromService(identifier, status);
            }
        }

        @Override
        public void onOffAgentStatusEventNotify(int userModestatus, int dispposnstate, int dispContentReq) throws android.os.RemoteException {
            manager = mUserModeMgr.get();

            if (manager != null) {
                manager.notifyOnOffAgentStatus(userModestatus, dispposnstate, dispContentReq);
            }
        }
    }
}
