package com.druvu.lib.fx.updater.fixtures;

import com.druvu.lib.fx.updater.Decision;
import com.druvu.lib.fx.updater.Installed;
import com.druvu.lib.fx.updater.Progress;
import com.druvu.lib.fx.updater.RestartChoice;
import com.druvu.lib.fx.updater.Update;
import com.druvu.lib.fx.updater.UpdateFailure;
import com.druvu.lib.fx.updater.UpdateHost;
import java.lang.module.ModuleDescriptor.Version;
import java.util.ArrayList;
import java.util.List;

/** A user whose answers are decided before the check runs, and who remembers everything shown. */
public final class ScriptedHost implements UpdateHost {

    private final Decision decision;
    private final RestartChoice restartChoice;
    private final List<Update> offers = new ArrayList<>();
    private final List<Progress> progresses = new ArrayList<>();
    private final List<Installed> installs = new ArrayList<>();
    private final List<Version> upToDates = new ArrayList<>();
    private final List<UpdateFailure> failures = new ArrayList<>();

    private ScriptedHost(Decision decision, RestartChoice restartChoice) {
        this.decision = decision;
        this.restartChoice = restartChoice;
    }

    public static ScriptedHost answering(Decision decision) {
        return new ScriptedHost(decision, RestartChoice.LATER);
    }

    public static ScriptedHost answering(Decision decision, RestartChoice restartChoice) {
        return new ScriptedHost(decision, restartChoice);
    }

    @Override
    public Decision offer(Update update) {
        offers.add(update);
        return decision;
    }

    @Override
    public void progress(Progress progress) {
        progresses.add(progress);
    }

    @Override
    public RestartChoice installed(Installed installed) {
        installs.add(installed);
        return restartChoice;
    }

    @Override
    public void upToDate(Version current) {
        upToDates.add(current);
    }

    @Override
    public void failed(UpdateFailure failure) {
        failures.add(failure);
    }

    public List<Update> offers() {
        return List.copyOf(offers);
    }

    public List<Progress> progresses() {
        return List.copyOf(progresses);
    }

    public List<Installed> installs() {
        return List.copyOf(installs);
    }

    public List<Version> upToDates() {
        return List.copyOf(upToDates);
    }

    public List<UpdateFailure> failures() {
        return List.copyOf(failures);
    }
}
