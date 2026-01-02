import SwiftUI

struct ContentView: View {
	var body: some View {
		VStack(spacing: 12) {
			Text("Mobilispect")
				.font(.title2)
				.fontWeight(.semibold)
			Text("iOS app scaffold")
				.font(.subheadline)
				.foregroundStyle(.secondary)
		}
		.padding()
	}
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
		ContentView()
	}
}
