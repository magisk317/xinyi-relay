const repository = process.env.GITHUB_REPOSITORY;

if (!repository) {
  throw new Error("GITHUB_REPOSITORY is required");
}

module.exports = {
  platform: "github",
  repositories: [repository],
  onboarding: false,
  requireConfig: "required",
};
