package jenkins.plugins.slack;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.listeners.ItemListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.plugins.slack.webhook.model.SlackUser;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlackNotifier extends Notifier {

    private static final Logger logger = Logger.getLogger(SlackNotifier.class.getName());

    private String teamDomain;
    private String authToken;
    private String buildServerUrl;
    private String room;
    private String apiToken;
    private String sendAs;
    private boolean startNotification;
    private boolean notifySuccess;
    private boolean notifyAborted;
    private boolean notifyNotBuilt;
    private boolean notifyUnstable;
    private boolean notifyFailure;
    private boolean notifyBackToNormal;
    private boolean notifyRepeatedFailure;
    private boolean includeTestSummary;
    private CommitInfoChoice commitInfoChoice;
    private boolean includeCustomMessage;
    private String customMessage;
    private boolean includeMention;
    private List<String> mentionList;

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public String getTeamDomain() {
        return teamDomain;
    }

    public String getRoom() {
        return room;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getBuildServerUrl() {
        if(buildServerUrl == null || buildServerUrl == "") {
            JenkinsLocationConfiguration jenkinsConfig = new JenkinsLocationConfiguration();
            return jenkinsConfig.getUrl();
        }
        else {
            return buildServerUrl;
        }
    }

    public String getSendAs() {
        return sendAs;
    }

    public boolean getStartNotification() {
        return startNotification;
    }

    public boolean getNotifySuccess() {
        return notifySuccess;
    }

    public CommitInfoChoice getCommitInfoChoice() {
        return commitInfoChoice;
    }

    public boolean getNotifyAborted() {
        return notifyAborted;
    }

    public boolean getNotifyFailure() {
        return notifyFailure;
    }

    public boolean getNotifyNotBuilt() {
        return notifyNotBuilt;
    }

    public boolean getNotifyUnstable() {
        return notifyUnstable;
    }

    public boolean getNotifyBackToNormal() {
        return notifyBackToNormal;
    }

    public boolean includeTestSummary() {
        return includeTestSummary;
    }

    public boolean getNotifyRepeatedFailure() {
        return notifyRepeatedFailure;
    }

    public boolean includeCustomMessage() {
        return includeCustomMessage;
    }

    public String getCustomMessage() {
        return customMessage;
    }

    public boolean includeMention() {
      return includeMention;
    }

    public List<String> getMentionList() {
      return mentionList;
    }

    @DataBoundConstructor
    public SlackNotifier(final String teamDomain, final String authToken, final String room, String apiToken, final String buildServerUrl,
                         final String sendAs, final boolean startNotification, final boolean notifyAborted, final boolean notifyFailure,
                         final boolean notifyNotBuilt, final boolean notifySuccess, final boolean notifyUnstable, final boolean notifyBackToNormal,
                         final boolean notifyRepeatedFailure, final boolean includeTestSummary, CommitInfoChoice commitInfoChoice,
                         boolean includeCustomMessage, String customMessage, boolean includeMention, List<String> mentionList) {
        super();
        this.teamDomain = teamDomain;
        this.authToken = authToken;
        this.buildServerUrl = buildServerUrl;
        this.room = room;
        this.apiToken = apiToken;
        this.sendAs = sendAs;
        this.startNotification = startNotification;
        this.notifyAborted = notifyAborted;
        this.notifyFailure = notifyFailure;
        this.notifyNotBuilt = notifyNotBuilt;
        this.notifySuccess = notifySuccess;
        this.notifyUnstable = notifyUnstable;
        this.notifyBackToNormal = notifyBackToNormal;
        this.notifyRepeatedFailure = notifyRepeatedFailure;
        this.includeTestSummary = includeTestSummary;
        this.commitInfoChoice = commitInfoChoice;
        this.includeCustomMessage = includeCustomMessage;
        this.customMessage = customMessage;
        this.includeMention = includeMention;
        this.mentionList = mentionList;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public SlackService newSlackService(AbstractBuild r, BuildListener listener) {
        String teamDomain = this.teamDomain;
        if (StringUtils.isEmpty(teamDomain)) {
            teamDomain = getDescriptor().getTeamDomain();
        }
        String authToken = this.authToken;
        if (StringUtils.isEmpty(authToken)) {
            authToken = getDescriptor().getToken();
        }
        String room = this.room;
        if (StringUtils.isEmpty(room)) {
            room = getDescriptor().getRoom();
        }
        String apiToken = this.apiToken;
        if (StringUtils.isEmpty(apiToken)) {
            apiToken = getDescriptor().getApiToken();
        }

        EnvVars env = null;
        try {
            env = r.getEnvironment(listener);
        } catch (Exception e) {
            listener.getLogger().println("Error retrieving environment vars: " + e.getMessage());
            env = new EnvVars();
        }
        teamDomain = env.expand(teamDomain);
        authToken = env.expand(authToken);
        room = env.expand(room);
        apiToken = env.expand(apiToken);

        return new StandardSlackService(teamDomain, authToken, room, apiToken);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        if (startNotification) {
            Map<Descriptor<Publisher>, Publisher> map = build.getProject().getPublishersList().toMap();
            for (Publisher publisher : map.values()) {
                if (publisher instanceof SlackNotifier) {
                    logger.info("Invoking Started...");
                    new ActiveNotifier((SlackNotifier) publisher, listener).started(build);
                }
            }
        }
        return super.prebuild(build, listener);
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String teamDomain;
        private String token;
        private String room;
        private String buildServerUrl;
        private String sendAs;
        private String apiToken;

        public static final CommitInfoChoice[] COMMIT_INFO_CHOICES = CommitInfoChoice.values();

        public DescriptorImpl() {
            load();
        }

        public String getTeamDomain() {
            return teamDomain;
        }

        public String getToken() {
            return token;
        }

        public String getRoom() {
            return room;
        }

        public String getBuildServerUrl() {
            if(buildServerUrl == null || buildServerUrl == "") {
                JenkinsLocationConfiguration jenkinsConfig = new JenkinsLocationConfiguration();
                return jenkinsConfig.getUrl();
            }
            else {
                return buildServerUrl;
            }
        }

        public String getSendAs() {
            return sendAs;
        }

        public String getApiToken() {
            return apiToken;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public SlackNotifier newInstance(StaplerRequest sr, JSONObject json) {
            String teamDomain = sr.getParameter("slackTeamDomain");
            String token = sr.getParameter("slackToken");
            String room = sr.getParameter("slackRoom");
            String apiToken = sr.getParameter("slackApiToken");
            boolean startNotification = "true".equals(sr.getParameter("slackStartNotification"));
            boolean notifySuccess = "true".equals(sr.getParameter("slackNotifySuccess"));
            boolean notifyAborted = "true".equals(sr.getParameter("slackNotifyAborted"));
            boolean notifyNotBuilt = "true".equals(sr.getParameter("slackNotifyNotBuilt"));
            boolean notifyUnstable = "true".equals(sr.getParameter("slackNotifyUnstable"));
            boolean notifyFailure = "true".equals(sr.getParameter("slackNotifyFailure"));
            boolean notifyBackToNormal = "true".equals(sr.getParameter("slackNotifyBackToNormal"));
            boolean notifyRepeatedFailure = "true".equals(sr.getParameter("slackNotifyRepeatedFailure"));
            boolean includeTestSummary = "true".equals(sr.getParameter("includeTestSummary"));
            CommitInfoChoice commitInfoChoice = CommitInfoChoice.forDisplayName(sr.getParameter("slackCommitInfoChoice"));
            boolean includeCustomMessage = "on".equals(sr.getParameter("includeCustomMessage"));
            String customMessage = sr.getParameter("customMessage");
            boolean includeMention = (json.optJSONObject("includeMention") != null);
            List<String> mentionList = new ArrayList<String>();

            if (includeMention) {
              JSONArray mentionArray = json.getJSONObject("includeMention").optJSONArray("mention");
              if (mentionArray != null) {
                for (Object mentionObj: mentionArray) {
                  JSONObject mention = JSONObject.fromObject(mentionObj);
                  mentionList.add(mention.getString("mentionTo"));
                }
              } else {
                JSONObject mention = json.getJSONObject("includeMention").optJSONObject("mention");
                if (mention != null) {
                  mentionList.add(mention.getString("mentionTo"));
                }
              }
            }


            return new SlackNotifier(teamDomain, token, room, apiToken, buildServerUrl, sendAs, startNotification, notifyAborted,
                    notifyFailure, notifyNotBuilt, notifySuccess, notifyUnstable, notifyBackToNormal, notifyRepeatedFailure,
                    includeTestSummary, commitInfoChoice, includeCustomMessage, customMessage, includeMention, mentionList);
        }

        @Override
        public boolean configure(StaplerRequest sr, JSONObject formData) throws FormException {
            teamDomain = sr.getParameter("slackTeamDomain");
            token = sr.getParameter("slackToken");
            room = sr.getParameter("slackRoom");
            apiToken = sr.getParameter("slackApiToken");
            buildServerUrl = sr.getParameter("slackBuildServerUrl");
            sendAs = sr.getParameter("slackSendAs");
            if(buildServerUrl == null || buildServerUrl == "") {
                JenkinsLocationConfiguration jenkinsConfig = new JenkinsLocationConfiguration();
                buildServerUrl = jenkinsConfig.getUrl();
            }
            if (buildServerUrl != null && !buildServerUrl.endsWith("/")) {
                buildServerUrl = buildServerUrl + "/";
            }
            save();
            return super.configure(sr, formData);
        }

        SlackService getSlackService(final String teamDomain, final String authToken, final String room, final String apiToken) {
            return new StandardSlackService(teamDomain, authToken, room, apiToken);
        }

        @Override
        public String getDisplayName() {
            return "Slack Notifications";
        }

        public ListBoxModel doFillMentionToItems(@QueryParameter("slackTeamDomain") final String teamDomain,
                                                 @QueryParameter("slackToken") final String authToken,
                                                 @QueryParameter("slackRoom") final String room,
                                                 @QueryParameter("slackApiToken") final String apiToken) {

            ListBoxModel items = new ListBoxModel();
            items.add("[ job triggerd user ]", Constants.JOB_TRIGGERD_MENTION);
            items.add("[ ghprb triggerd user ]", Constants.GHPRB_MENTION);
            items.add("@channel", "channel");
            items.add("@here", "here");
            try {
                String targetDomain = teamDomain;
                if (StringUtils.isEmpty(targetDomain)) {
                    targetDomain = this.teamDomain;
                }
                String targetToken = authToken;
                if (StringUtils.isEmpty(targetToken)) {
                    targetToken = this.token;
                }
                String targetRoom = room;
                if (StringUtils.isEmpty(targetRoom)) {
                    targetRoom = this.room;
                }
                String targetApiToken = apiToken;
                if (StringUtils.isEmpty(targetApiToken)) {
                    targetApiToken = this.apiToken;
                }

                SlackService slackService = getSlackService(targetDomain, targetToken, targetRoom, targetApiToken);
                for (SlackUser user: slackService.getUserList()) {
                  if (!user.isDeleted()) {
                    items.add("@" + user.getName(), user.getName());
                  }
                }

            } catch (Exception e) {
              // ignore
            }

            return items;
        }

        public FormValidation doTestConnection(@QueryParameter("slackTeamDomain") final String teamDomain,
                                               @QueryParameter("slackToken") final String authToken,
                                               @QueryParameter("slackRoom") final String room,
                                               @QueryParameter("slackApiToken") final String apiToken,
                                               @QueryParameter("slackBuildServerUrl") final String buildServerUrl) throws FormException {
            try {
                String targetDomain = teamDomain;
                if (StringUtils.isEmpty(targetDomain)) {
                    targetDomain = this.teamDomain;
                }
                String targetToken = authToken;
                if (StringUtils.isEmpty(targetToken)) {
                    targetToken = this.token;
                }
                String targetRoom = room;
                if (StringUtils.isEmpty(targetRoom)) {
                    targetRoom = this.room;
                }
                String targetApiToken = apiToken;
                if (StringUtils.isEmpty(targetApiToken)) {
                    targetApiToken = this.apiToken;
                }
                String targetBuildServerUrl = buildServerUrl;
                if (StringUtils.isEmpty(targetBuildServerUrl)) {
                    targetBuildServerUrl = this.buildServerUrl;
                }
                SlackService testSlackService = getSlackService(targetDomain, targetToken, targetRoom, targetApiToken);
                String message = "Slack/Jenkins plugin: you're all set on " + targetBuildServerUrl;
                boolean success = testSlackService.publish(message, "good");
                return success ? FormValidation.ok("Success") : FormValidation.error("Failure");
            } catch (Exception e) {
                return FormValidation.error("Client error : " + e.getMessage());
            }
        }
    }

    @Deprecated
    public static class SlackJobProperty extends hudson.model.JobProperty<AbstractProject<?, ?>> {

        private String teamDomain;
        private String token;
        private String room;
        private boolean startNotification;
        private boolean notifySuccess;
        private boolean notifyAborted;
        private boolean notifyNotBuilt;
        private boolean notifyUnstable;
        private boolean notifyFailure;
        private boolean notifyBackToNormal;
        private boolean notifyRepeatedFailure;
        private boolean includeTestSummary;
        private boolean showCommitList;
        private boolean includeCustomMessage;
        private String customMessage;

        @DataBoundConstructor
        public SlackJobProperty(String teamDomain,
                                String token,
                                String room,
                                boolean startNotification,
                                boolean notifyAborted,
                                boolean notifyFailure,
                                boolean notifyNotBuilt,
                                boolean notifySuccess,
                                boolean notifyUnstable,
                                boolean notifyBackToNormal,
                                boolean notifyRepeatedFailure,
                                boolean includeTestSummary,
                                boolean showCommitList,
                                boolean includeCustomMessage,
                                String customMessage) {
            this.teamDomain = teamDomain;
            this.token = token;
            this.room = room;
            this.startNotification = startNotification;
            this.notifyAborted = notifyAborted;
            this.notifyFailure = notifyFailure;
            this.notifyNotBuilt = notifyNotBuilt;
            this.notifySuccess = notifySuccess;
            this.notifyUnstable = notifyUnstable;
            this.notifyBackToNormal = notifyBackToNormal;
            this.notifyRepeatedFailure = notifyRepeatedFailure;
            this.includeTestSummary = includeTestSummary;
            this.showCommitList = showCommitList;
            this.includeCustomMessage = includeCustomMessage;
            this.customMessage = customMessage;
        }

        @Exported
        public String getTeamDomain() {
            return teamDomain;
        }

        @Exported
        public String getToken() {
            return token;
        }

        @Exported
        public String getRoom() {
            return room;
        }

        @Exported
        public boolean getStartNotification() {
            return startNotification;
        }

        @Exported
        public boolean getNotifySuccess() {
            return notifySuccess;
        }

        @Exported
        public boolean getShowCommitList() {
            return showCommitList;
        }

        @Override
        public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
            return super.prebuild(build, listener);
        }

        @Exported
        public boolean getNotifyAborted() {
            return notifyAborted;
        }

        @Exported
        public boolean getNotifyFailure() {
            return notifyFailure;
        }

        @Exported
        public boolean getNotifyNotBuilt() {
            return notifyNotBuilt;
        }

        @Exported
        public boolean getNotifyUnstable() {
            return notifyUnstable;
        }

        @Exported
        public boolean getNotifyBackToNormal() {
            return notifyBackToNormal;
        }

        @Exported
        public boolean includeTestSummary() {
            return includeTestSummary;
        }

        @Exported
        public boolean getNotifyRepeatedFailure() {
            return notifyRepeatedFailure;
        }

        @Exported
        public boolean includeCustomMessage() {
            return includeCustomMessage;
        }

        @Exported
        public String getCustomMessage() {
            return customMessage;
        }

    }

    @Extension public static final class Migrator extends ItemListener {
        @SuppressWarnings("deprecation")
        @Override
        public void onLoaded() {
            logger.info("Starting Settings Migration Process");
            for (AbstractProject<?, ?> p : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                logger.info("processing Job: " + p.getName());

                final SlackJobProperty slackJobProperty = p.getProperty(SlackJobProperty.class);

                if (slackJobProperty == null) {
                    logger.info(String
                            .format("Configuration is already up to date for \"%s\", skipping migration",
                                    p.getName()));
                    continue;
                }

                SlackNotifier slackNotifier = p.getPublishersList().get(SlackNotifier.class);

                if (slackNotifier == null) {
                    logger.info(String
                            .format("Configuration does not have a notifier for \"%s\", not migrating settings",
                                    p.getName()));
                } else {

                    //map settings
                    if (StringUtils.isBlank(slackNotifier.teamDomain)) {
                        slackNotifier.teamDomain = slackJobProperty.getTeamDomain();
                    }
                    if (StringUtils.isBlank(slackNotifier.authToken)) {
                        slackNotifier.authToken = slackJobProperty.getToken();
                    }
                    if (StringUtils.isBlank(slackNotifier.room)) {
                        slackNotifier.room = slackJobProperty.getRoom();
                    }

                    slackNotifier.startNotification = slackJobProperty.getStartNotification();

                    slackNotifier.notifyAborted = slackJobProperty.getNotifyAborted();
                    slackNotifier.notifyFailure = slackJobProperty.getNotifyFailure();
                    slackNotifier.notifyNotBuilt = slackJobProperty.getNotifyNotBuilt();
                    slackNotifier.notifySuccess = slackJobProperty.getNotifySuccess();
                    slackNotifier.notifyUnstable = slackJobProperty.getNotifyUnstable();
                    slackNotifier.notifyBackToNormal = slackJobProperty.getNotifyBackToNormal();
                    slackNotifier.notifyRepeatedFailure = slackJobProperty.getNotifyRepeatedFailure();

                    slackNotifier.includeTestSummary = slackJobProperty.includeTestSummary();
                    slackNotifier.commitInfoChoice = slackJobProperty.getShowCommitList() ? CommitInfoChoice.AUTHORS_AND_TITLES : CommitInfoChoice.NONE;
                    slackNotifier.includeCustomMessage = slackJobProperty.includeCustomMessage();
                    slackNotifier.customMessage = slackJobProperty.getCustomMessage();
                    slackNotifier.includeMention = false;
                    slackNotifier.mentionList = new ArrayList<String>();
                }

                try {
                    //property section is not used anymore - remove
                    p.removeProperty(SlackJobProperty.class);
                    p.save();
                    logger.info("Configuration updated successfully");
                } catch (IOException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            }
        }
    }
}
