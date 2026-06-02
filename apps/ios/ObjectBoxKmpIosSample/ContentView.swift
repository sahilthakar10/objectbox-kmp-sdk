import ObjectBoxKmpSampleShared
import SwiftUI

final class CrudViewModel: ObservableObject {
    private let sample = CommonCrudSample()
    private var flowSubscription: ObxSampleSubscription?

    @Published var output: String
    @Published var status: String = "Flow observer active"

    init() {
        output = sample.currentState()
        flowSubscription = sample.observeRenderedState { [weak self] renderedState in
            DispatchQueue.main.async {
                self?.output = renderedState
                self?.status = "Flow update received"
            }
        }
    }

    deinit {
        flowSubscription?.cancel()
    }

    func run(_ status: String, _ operation: () -> String) {
        output = operation()
        self.status = status
    }

    func create() { run("Create") { sample.create() } }
    func readSelected() { run("Read") { sample.readSelected() } }
    func updateSelected() { run("Update") { sample.updateSelected() } }
    func deleteSelected() { run("Delete") { sample.deleteSelected() } }
    func createThree() { run("Create 3") { sample.createThree() } }
    func clearAll() { run("Clear All") { sample.clearAll() } }
    func findOpenNotes() { run("Open Notes") { sample.findOpenNotes() } }
    func searchTitles() { run("Search Titles") { sample.searchTitles() } }
    func countDoneNotes() { run("Count Done") { sample.countDoneNotes() } }
    func deleteDoneNotes() { run("Delete Done") { sample.deleteDoneNotes() } }
    func seedComplexNotes() { run("Seed Data") { sample.seedComplexNotes() } }
    func complexAndRangeQuery() { run("AND + Range") { sample.complexAndRangeQuery() } }
    func complexGroupedOrQuery() { run("Grouped OR") { sample.complexGroupedOrQuery() } }
    func complexDeleteQuery() { run("Complex Delete") { sample.complexDeleteQuery() } }
}

struct ContentView: View {
    @StateObject private var viewModel = CrudViewModel()

    private let columns = [
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8)
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HeroPanel(status: viewModel.status)

                ActionSection(
                    title: "Core CRUD",
                    actions: [
                        SampleAction("Create", "Insert one Note", viewModel.create),
                        SampleAction("Read", "Read selected", viewModel.readSelected),
                        SampleAction("Update", "Toggle + rename", viewModel.updateSelected),
                        SampleAction("Delete", "Remove selected", viewModel.deleteSelected, .danger),
                        SampleAction("Create 3", "Batch insert", viewModel.createThree),
                        SampleAction("Clear All", "Reset sample", viewModel.clearAll, .danger)
                    ],
                    columns: columns
                )

                ActionSection(
                    title: "Queries",
                    actions: [
                        SampleAction("Open Notes", "done = false", viewModel.findOpenNotes),
                        SampleAction("Search Titles", "contains + limit", viewModel.searchTitles),
                        SampleAction("Count Done", "aggregate count", viewModel.countDoneNotes),
                        SampleAction("Delete Done", "query remove", viewModel.deleteDoneNotes, .danger)
                    ],
                    columns: columns
                )

                ActionSection(
                    title: "Complex Query DSL",
                    actions: [
                        SampleAction("Seed Data", "Realistic notes", viewModel.seedComplexNotes),
                        SampleAction("AND + Range", "filters + sort", viewModel.complexAndRangeQuery),
                        SampleAction("Grouped OR", "anyOf + limit", viewModel.complexGroupedOrQuery),
                        SampleAction("Complex Delete", "allOf remove", viewModel.complexDeleteQuery, .danger)
                    ],
                    columns: columns
                )

                OutputPanel(output: viewModel.output)
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Color(red: 0.965, green: 0.976, blue: 0.992))
    }
}

private struct HeroPanel: View {
    let status: String

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("ObjectBox KMP SDK")
                .font(.system(size: 30, weight: .bold))
                .foregroundStyle(.primary)

            Text("CommonMain CRUD, query DSL, migrations, and Flow changes running through the shared SDK API.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            Text(status)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color(red: 0.04, green: 0.36, blue: 0.30))
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color(red: 0.86, green: 0.97, blue: 0.93))
                .clipShape(Capsule())
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .panelStyle()
    }
}

private struct ActionSection: View {
    let title: String
    let actions: [SampleAction]
    let columns: [GridItem]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundStyle(.primary)

            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(actions) { action in
                    Button(action: action.run) {
                        VStack(spacing: 4) {
                            Text(action.title)
                                .font(.subheadline.weight(.semibold))
                                .multilineTextAlignment(.center)
                            Text(action.subtitle)
                                .font(.caption)
                                .multilineTextAlignment(.center)
                                .opacity(0.92)
                        }
                        .frame(maxWidth: .infinity, minHeight: 70)
                        .padding(.horizontal, 8)
                    }
                    .buttonStyle(SampleButtonStyle(tone: action.tone))
                }
            }
        }
        .padding(16)
        .panelStyle()
    }
}

private struct OutputPanel: View {
    let output: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Live Output")
                .font(.headline)
                .foregroundStyle(.primary)

            ScrollView(.horizontal, showsIndicators: true) {
                Text(output)
                    .font(.system(.body, design: .monospaced))
                    .foregroundStyle(Color(red: 0.90, green: 0.92, blue: 0.95))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(red: 0.07, green: 0.10, blue: 0.15))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .padding(16)
        .panelStyle()
    }
}

private struct SampleAction: Identifiable {
    enum Tone {
        case primary
        case danger
    }

    let id = UUID()
    let title: String
    let subtitle: String
    let run: () -> Void
    let tone: Tone

    init(
        _ title: String,
        _ subtitle: String,
        _ run: @escaping () -> Void,
        _ tone: Tone = .primary
    ) {
        self.title = title
        self.subtitle = subtitle
        self.run = run
        self.tone = tone
    }
}

private struct SampleButtonStyle: ButtonStyle {
    let tone: SampleAction.Tone

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(.white)
            .background(background.opacity(configuration.isPressed ? 0.82 : 1.0))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var background: Color {
        switch tone {
        case .primary:
            return Color(red: 0.05, green: 0.48, blue: 0.40)
        case .danger:
            return Color(red: 0.70, green: 0.14, blue: 0.09)
        }
    }
}

private extension View {
    func panelStyle() -> some View {
        self
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color(red: 0.85, green: 0.88, blue: 0.92), lineWidth: 1)
            )
    }
}
