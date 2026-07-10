import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from scipy.stats import ttest_ind
import seaborn as sns

# ==========================
# LOAD DATA
# ==========================

FILE = "suite_log.csv"

df = pd.read_csv(FILE)

engines = df["engine"].unique()

print("\nEngines:")
for e in engines:
    print("  ", e)

# ==========================
# BASIC SUMMARY
# ==========================

print("\n==============================")
print("OVERALL SUMMARY")
print("==============================")

summary = (
    df.groupby("engine")
      .agg({
          "depthReached": ["mean", "median", "max"],
          "nodesSearched": ["mean", "median"],
          "elapsedMs": ["mean", "median"],
          "nodesPerSecond": ["mean", "median"]
      })
)

print(summary)

# ==========================
# DEPTH DISTRIBUTION
# ==========================

plt.figure(figsize=(10,6))

for e in engines:
    subset = df[df.engine == e]
    plt.hist(
        subset["depthReached"],
        bins=30,
        alpha=0.5,
        label=e
    )

plt.title("Depth Distribution")
plt.xlabel("Depth")
plt.ylabel("Count")
plt.legend()
plt.tight_layout()
plt.show()

# ==========================
# TIME DISTRIBUTION
# ==========================

plt.figure(figsize=(10,6))

for e in engines:
    subset = df[df.engine == e]
    plt.hist(
        subset["elapsedMs"],
        bins=50,
        alpha=0.5,
        label=e
    )

plt.title("Time Usage Distribution")
plt.xlabel("Elapsed (ms)")
plt.ylabel("Count")
plt.legend()
plt.tight_layout()
plt.show()

# ==========================
# NPS DISTRIBUTION
# ==========================

plt.figure(figsize=(10,6))

for e in engines:
    subset = df[df.engine == e]
    plt.hist(
        subset["nodesPerSecond"],
        bins=50,
        alpha=0.5,
        label=e
    )

plt.title("Nodes Per Second")
plt.xlabel("NPS")
plt.ylabel("Count")
plt.legend()
plt.tight_layout()
plt.show()

# ==========================
# NODES SEARCHED
# ==========================

plt.figure(figsize=(10,6))

for e in engines:
    subset = df[df.engine == e]
    plt.hist(
        np.log10(subset["nodesSearched"] + 1),
        bins=50,
        alpha=0.5,
        label=e
    )

plt.title("Nodes Searched (log10)")
plt.xlabel("log10(nodes)")
plt.ylabel("Count")
plt.legend()
plt.tight_layout()
plt.show()

# ==========================
# MOVE NUMBER ANALYSIS
# ==========================

move_stats = (
    df.groupby(["engine", "moveNumber"])
      .agg({
          "depthReached":"mean",
          "elapsedMs":"mean",
          "nodesPerSecond":"mean",
          "nodesSearched":"mean"
      })
      .reset_index()
)

metrics = [
    "depthReached",
    "elapsedMs",
    "nodesPerSecond",
    "nodesSearched"
]

for metric in metrics:

    plt.figure(figsize=(12,6))

    for e in engines:
        subset = move_stats[move_stats.engine == e]

        plt.plot(
            subset["moveNumber"],
            subset[metric],
            label=e
        )

    plt.title(f"{metric} vs Move Number")
    plt.xlabel("Move")
    plt.ylabel(metric)
    plt.legend()
    plt.tight_layout()
    plt.show()

# ==========================
# GAME PHASE ANALYSIS
# ==========================

def phase(move):
    if move < 20:
        return "Opening"
    elif move < 60:
        return "Middlegame"
    else:
        return "Endgame"

df["phase"] = df["moveNumber"].apply(phase)

phase_summary = (
    df.groupby(["engine", "phase"])
      .agg({
          "depthReached":"mean",
          "elapsedMs":"mean",
          "nodesPerSecond":"mean",
          "nodesSearched":"mean"
      })
)

print("\n==============================")
print("PHASE ANALYSIS")
print("==============================")
print(phase_summary)

# ==========================
# WHITE VS BLACK
# ==========================

side_summary = (
    df.groupby(["engine", "side"])
      .agg({
          "depthReached":"mean",
          "elapsedMs":"mean",
          "nodesPerSecond":"mean",
          "nodesSearched":"mean"
      })
)

print("\n==============================")
print("SIDE ANALYSIS")
print("==============================")
print(side_summary)

# ==========================
# EFFICIENCY
# ==========================

efficiency = (
    df.groupby("engine")
      .apply(
          lambda g: pd.Series({
              "avgDepth": g.depthReached.mean(),
              "avgNodes": g.nodesSearched.mean(),
              "avgTimeMs": g.elapsedMs.mean(),
              "avgNPS": g.nodesPerSecond.mean(),
              "depthPerMillionNodes":
                  g.depthReached.mean()
                  /
                  (g.nodesSearched.mean() / 1_000_000 + 1e-9)
          })
      )
)

print("\n==============================")
print("EFFICIENCY")
print("==============================")
print(efficiency)

# ==========================
# CORRELATIONS
# ==========================

print("\n==============================")
print("CORRELATIONS")
print("==============================")

for e in engines:

    subset = df[df.engine == e]

    print(f"\n{e}")

    corr = subset[
        [
            "depthReached",
            "nodesSearched",
            "elapsedMs",
            "nodesPerSecond"
        ]
    ].corr()

    print(corr)

# ==========================
# SIGNIFICANCE TESTS
# ==========================

if len(engines) == 2:

    e1 = df[df.engine == engines[0]]
    e2 = df[df.engine == engines[1]]

    metrics = [
        "depthReached",
        "nodesSearched",
        "elapsedMs",
        "nodesPerSecond"
    ]

    print("\n==============================")
    print("T-TESTS")
    print("==============================")

    for metric in metrics:

        t,p = ttest_ind(
            e1[metric],
            e2[metric],
            equal_var=False
        )

        print(
            f"{metric:20s}"
            f"  p={p:.6g}"
        )

# ==========================
# BOXPLOTS
# ==========================

for metric in [
    "depthReached",
    "elapsedMs",
    "nodesPerSecond",
    "nodesSearched"
]:

    plt.figure(figsize=(8,6))

    sns.boxplot(
        data=df,
        x="engine",
        y=metric
    )

    plt.title(metric)
    plt.tight_layout()
    plt.show()

print("\nDone.")