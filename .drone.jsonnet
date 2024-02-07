// Intentionally doing a depth of 2 as libSession-util has it's own submodules (and libLokinet likely will as well)
local clone_submodules = {
  name: 'Clone Submodules',
  commands: ['git fetch --tags', 'git submodule update --init --recursive --depth=2']
};

// cmake options for static deps mirror
local ci_dep_mirror(want_mirror) = (if want_mirror then ' -DLOCAL_MIRROR=https://oxen.rocks/deps ' else '');

[
  // Unit tests (PRs only)
  {
    kind: 'pipeline',
    type: 'exec',
    name: 'Unit Tests',
    platform: { arch: 'amd64' },
    trigger: { event: { exclude: [ 'push' ] } },
    steps: [
      clone_submodules,
      {
        name: 'Run Unit Tests',
        image: 'registry.oxen.rocks/lokinet-ci-android',
        commands: [
          './gradlew testPlayDebugUnitTestCoverageReport'
        ],
      }
    ],
  },
  // Validate build artifact was created by the direct branch push (PRs only)
  {
    kind: 'pipeline',
    type: 'exec',
    name: 'Check Build Artifact Existence',
    platform: { arch: 'amd64' },
    trigger: { event: { exclude: [ 'push' ] } },
    steps: [
      {
        name: 'Poll for build artifact existence',
        commands: [
          './Scripts/drone-upload-exists.sh'
        ]
      }
    ]
  },
  // Debug APK build (non-PRs only)
  {
    kind: 'pipeline',
    type: 'exec',
    name: 'Debug APK Build',
    platform: { arch: 'amd64' },
    trigger: { event: { exclude: [ 'pull_request' ] } },
    steps: [
      clone_submodules,
      {
        name: 'Build',
        image: 'registry.oxen.rocks/lokinet-ci-android',
        commands: [
          './gradlew assemblePlayDebug'
        ],
      },
      {
        name: 'Upload artifacts',
        environment: { SSH_KEY: { from_secret: 'SSH_KEY' } },
        commands: [
          './Scripts/drone-static-upload.sh'
        ]
      },
    ],
  }
]